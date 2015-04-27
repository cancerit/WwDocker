/**
 * Copyright (c) 2015 Genome Research Ltd.
 * 
 * Author: Cancer Genome Project cgpit@sanger.ac.uk
 * 
 * This file is part of WwDocker.
 * 
 * WwDocker is free software: you can redistribute it and/or modify it under
 * the terms of the GNU Affero General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 * 
 * 1. The usage of a range of years within a copyright statement contained within
 * this distribution should be interpreted as being equivalent to a list of years
 * including the first and last year specified and all consecutive years between
 * them. For example, a copyright statement that reads 'Copyright (c) 2005, 2007-
 * 2009, 2011-2012' should be interpreted as being identical to a statement that
 * reads 'Copyright (c) 2005, 2007, 2008, 2009, 2011, 2012' and a copyright
 * statement that reads "Copyright (c) 2005-2012' should be interpreted as being
 * identical to a statement that reads 'Copyright (c) 2005, 2006, 2007, 2008,
 * 2009, 2010, 2011, 2012'."
 */

package uk.ac.sanger.cgp.wwdocker.daemon;

import com.jcraft.jsch.Session;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.ac.sanger.cgp.wwdocker.Config;
import uk.ac.sanger.cgp.wwdocker.actions.Local;
import uk.ac.sanger.cgp.wwdocker.actions.Remote;
import uk.ac.sanger.cgp.wwdocker.actions.Utils;
import uk.ac.sanger.cgp.wwdocker.beans.WorkerState;
import uk.ac.sanger.cgp.wwdocker.beans.WorkflowIni;
import uk.ac.sanger.cgp.wwdocker.callable.PullWork;
import uk.ac.sanger.cgp.wwdocker.callable.PushWork;
import uk.ac.sanger.cgp.wwdocker.enums.HostStatus;
import uk.ac.sanger.cgp.wwdocker.factories.WorkflowFactory;
import uk.ac.sanger.cgp.wwdocker.interfaces.Daemon;
import uk.ac.sanger.cgp.wwdocker.interfaces.Workflow;
import uk.ac.sanger.cgp.wwdocker.messages.Messaging;

/**
 *
 * @author kr2
 */
public class PrimaryDaemon implements Daemon {
  private static final Logger logger = LogManager.getLogger();
  
  private static PropertiesConfiguration config;
  private static Messaging messaging;
  
  // don't clean wwd_PEND, you will loose your ini's already queued and moved
  private static final String[] queuesToClean = {"wwd-active", "wwd_CLEAN", "wwd_DONE", "wwd_ERROR", "wwd_RUNNING"};
  
  private static ExecutorService pushExecutor = Executors.newSingleThreadExecutor();
  private static FutureTask<Integer> pushTask = null;
  private static FutureTask<Integer> pullTask = null;
  private static PushWork pushThread = null;
  private static PullWork pullThread = null;
  private static WorkerState pushToWorker = null;
  private static WorkerState pullFromWorker = null;
  
  public PrimaryDaemon(PropertiesConfiguration config, Messaging rmq) {
    PrimaryDaemon.config = config;
    PrimaryDaemon.messaging = rmq;
  }
  
  @Override
  public void run(String mode) throws IOException, InterruptedException, ConfigurationException {
    // lots of values that will be used over and over again
    File thisJar = Utils.thisJarFile();
    File tmpConf = new File(System.getProperty("java.io.tmpdir") + "/remote.cfg");
    tmpConf.deleteOnExit(); // contains passwords so cleanup
    config.save(tmpConf.getAbsolutePath()); // done like this so includes are pulled in
    Local.chmod(tmpConf, "go-rwx");

    // setup
    Workflow workManager = new WorkflowFactory().getWorkflow(config);
    Map<String,String> envs = Config.getEnvs(config);
    
     /*
     * gets the list of worker hosts which CAN change during runtime
     * There is a way to get the config to load when changed, however it only
     * matters when we loop round so we may as well just rebuild the object
     */ 
    Map <String, String> hosts = new LinkedHashMap<>();
    hostSet(config, hosts);
    cleanHostQueues(hosts);
    
    if(mode != null && mode.equalsIgnoreCase("KILLALL")) {
      killAll(hosts, thisJar, tmpConf);
    }
    
    hosts.clear(); // needs to be clean before looping starts
    
    // this holds md5 of this JAR and the config (which lists the workflow code to use)
    WorkerState provState = new WorkerState(thisJar, tmpConf);
    
    while(true) {
      addWorkToPend(workManager, config);
      
      // this is a reload as this can change during execution
      hostSet(config, hosts);
      
      for(Map.Entry<String,String> e : hosts.entrySet()) {
        if(e.getValue().equals("CURRENT")) {
          continue;
        }
        
        String host = e.getKey();
        if(e.getValue().equals("KILL")) {
          provState.setChangeStatusTo(HostStatus.KILL);
          messaging.sendMessage("wwd_"+host, Utils.objectToJson(provState));
          hosts.replace(host, "DELETE");
          continue;
        }
        
        provState.setChangeStatusTo(HostStatus.CHECKIN);
        provState.setReplyToQueue("wwd-active");
        if(e.getValue().equals("TO_PROVISION")) {
          if(!messaging.queryGaveResponse("wwd_"+host, provState.getReplyToQueue(), Utils.objectToJson(provState), 3000)) {
            logger.info("No response from host '".concat(host).concat("' (re)provisioning..."));
            provisionHost(host, config, thisJar, tmpConf, mode, envs);
          }
          hosts.replace(host, "CURRENT");
          break; // so we start some work on this host before provisioning more
        }
      }
      // we need a little sleep here or we'll kill the queues
      Thread.sleep(10000);
    }
  }
  
  private void hostSet(BaseConfiguration config, Map<String, String> hosts) {
    // first remove the killed off hosts
    List toRemove = new ArrayList();
    for(Map.Entry<String,String> e : hosts.entrySet()) {
      if(e.getValue().equals("DELETE")) {
        toRemove.add(e.getKey());
      }
    }
    hosts.keySet().removeAll(toRemove);
    
    // add the new hosts
    BaseConfiguration workerConf = Config.loadWorkers(config.getString("workerCfg"));
    String[] rawHosts = workerConf.getStringArray("hosts");
    if(rawHosts.length == 1 && rawHosts[0].equals(new String())) {
      rawHosts = new String[0];
    }
    Set<String> tmp = new LinkedHashSet<>();
    for(String h : rawHosts) {
      if(!hosts.containsKey(h)) {
        hosts.put(h, "TO_PROVISION");
      }
      tmp.add(h);
    }
    
    // identify which hosts need to be killed
    for(Map.Entry<String,String> e : hosts.entrySet()) {
      if(!tmp.contains(e.getKey())) {
        hosts.replace(e.getKey(), "KILL");
      }
    }
  }
  
  private void addWorkToPend(Workflow workManager, BaseConfiguration config) throws IOException, InterruptedException {
    // send all work into the wwd_PEND queue, data can be added during execution, this ensures duplicates don't occur
    List<File> iniFiles = Utils.getWorkInis(config);
    if(iniFiles.isEmpty()) {
      return;
    }
    
    // get all of the existing iniFiles so we can generate a uniq list
    List<String> existing = messaging.getMessageStrings("wwd_PEND", -1);
    Map<String, WorkflowIni> allInis= new HashMap();
    for (String m : existing) {
      WorkflowIni iniFile = (WorkflowIni) Utils.jsonToObject(m, WorkflowIni.class);
      allInis.put(iniFile.getIniFile().getAbsolutePath(), iniFile);
    }
    for(File iniFile : iniFiles) {
      if(!allInis.containsKey(iniFile.getAbsolutePath())) {
        WorkflowIni newIni = new WorkflowIni(iniFile);
        newIni.setLogSearchCmd(workManager.getFindLogsCmd());
        allInis.put(iniFile.getAbsolutePath(), newIni);
      }
    }

    Iterator itr = allInis.values().iterator();
    while(itr.hasNext()) {
      messaging.sendMessage("wwd_PEND", (WorkflowIni)itr.next());
    }
    
    workManager.iniUpdate(iniFiles, config, HostStatus.PEND);
  }
  
  private void provisionHost(String host, BaseConfiguration config, File thisJar, File tmpConf, String mode, Map<String,String> envs) throws InterruptedException {
    String[] makePaths = config.getStringArray("makePaths");
    String[] cleanPaths = config.getStringArray("cleanPaths");
    String remoteWorkflowDir = config.getString("workflowDir");
    String localSeqwareJar = config.getString("seqware"); // can be http location
    String localWorkflowZip = config.getString("workflow"); // can be http location
    File jreDist = Utils.expandUserFile(config, "jreDist", true);
    File remoteSeqwareJar = new File(remoteWorkflowDir.concat("/").concat(localSeqwareJar.replaceAll(".*/", "")));
    File remoteWorkflowZip = new File(remoteWorkflowDir.concat("/").concat(localWorkflowZip.replaceAll(".*/", "")));
    String baseDockerImage = config.getString("baseDockerImage");
    String optDir = "/opt";
    String workerLog = config.getString("log4-worker");
    File localTmp = Utils.expandUserDirPath(config, "primaryLargeTmp", true);
    
    Session ssh = Remote.getSession(config, host);


    // clean and setup paths
//    if(mode == null || !mode.equalsIgnoreCase("test")) {
//      Remote.cleanHost(ssh, cleanPaths);
//    }
    Remote.createPaths(ssh, makePaths);
    Remote.chmodPaths(ssh, "a+wrx", makePaths, true);
    Remote.cleanFiles(ssh, new String[]{config.getString("log4-delete")});

    // setup docker and the seqware image
    Remote.stageDocker(ssh, baseDockerImage);
    Local.pushToHost(localSeqwareJar, host, remoteWorkflowDir, envs, ssh, localTmp);
    // send jre
    Local.pushToHost(jreDist.getAbsolutePath(), host, optDir, envs, ssh, localTmp);
    Remote.expandJre(ssh, jreDist);

    // send the workflow
    Local.pushToHost(localWorkflowZip, host, remoteWorkflowDir, envs, ssh, localTmp);
    Remote.expandWorkflow(ssh, remoteWorkflowZip, remoteSeqwareJar, remoteWorkflowDir);

    // send the code needed to run the worker daemon, 
    // DON'T send required items after this point as starting the daemon signifies successful setup
    Local.pushToHost(thisJar.getAbsolutePath(), host, optDir, envs, ssh, localTmp);
    Local.pushToHost(workerLog, host, optDir, envs, ssh, localTmp);
    // config file
    Local.pushToHost(tmpConf.getAbsolutePath(), host, optDir, envs, ssh, localTmp);
    Remote.chmodPath(ssh, "go-wrx", optDir.concat("/*"), true); // file will have passwords
    
    Local.pushFileSetToHost(Utils.getGnosKeys(config), host, remoteWorkflowDir, envs, ssh, localTmp);

    Remote.startWorkerDaemon(ssh, thisJar.getName(), mode);
  }
  
  private void killAll(Map<String,String> hosts, File thisJar, File thisConf) throws IOException, InterruptedException {
    WorkerState killState = new WorkerState(thisJar, thisConf);
    killState.setChangeStatusTo(HostStatus.KILL);
    String killJson = Utils.objectToJson(killState);
    for(Map.Entry<String,String> e : hosts.entrySet()) {
      messaging.sendMessage("wwd_"+e.getKey(), killJson);
    }
    logger.fatal("All hosts shutting down as requested... exiting");
    System.exit(0);
  }
  
  private void cleanHostQueues(Map<String,String> hosts) throws IOException, InterruptedException {
    for(Map.Entry<String,String> e : hosts.entrySet()) {
      messaging.getMessageStrings("wwd_"+e.getKey(), 50);
    }
  }
  
//  private void startPush(Workflow workManager, WorkerState wsIn, Map<String,String> envs) throws IOException, InterruptedException {
//    if(pushTask != null) {
//      return;
//    }
//
//    pushToWorker = wsIn;
//    String host = pushToWorker.getResource().getHostName();
//
//    logger.debug("Should be starting to look for data");
//
//    // okay get some work if it exists
//    String message = messaging.getMessageString("wwd_PEND", 50);
//
//    if(message == null) {
//      return;
//    }
//
//
//
//    File iniFile = (File) Utils.jsonToObject(message, File.class); // this is the original path before loading
//    String iniFileName = workManager.iniPathByState(config, iniFile.getAbsolutePath(), HostStatus.PEND);
//    iniFile = new File(iniFileName);
//
//    logger.debug("FOUND:" + iniFile.getAbsolutePath());
//
//
//    // tell worker to change state
//    pushToWorker.setChangeStatusTo(HostStatus.RECEIVE);
//    pushToWorker.setWorkflowIni(iniFile);
//    messaging.sendMessage("wwd_"+host, Utils.objectToJson(pushToWorker));
//    messaging.getMessageString("wwd-active", -1); // clean the response
//
//    pushThread = new PushWork(iniFile.getName(), config, host, workManager.filesToPush(iniFile), envs);
//    pushTask = new FutureTask<Integer>(pushThread);
//    pushExecutor.execute(pushTask);
//  }
//
//  private void checkPush() throws IOException, InterruptedException {
//    if(pushTask == null) {
//      logger.trace("checkPush: nothing");
//      return;
//    }
//    logger.trace("checkPush: something");
//    if(pushTask.isDone()) {
//      logger.trace("checkPush: done");
//      String sentTo = pushThread.getHost();
//      int pushExitCode;
//      try {
//        pushExitCode = pushTask.get();
//      } catch(ExecutionException e) {
//        pushExitCode = 1;
//        pushToWorker.setError(e.getMessage());
//      }
//      if(pushExitCode == 0) {
//        pushToWorker.setChangeStatusTo(HostStatus.RUNNING);
//        messaging.sendMessage("wwd_"+sentTo, Utils.objectToJson(pushToWorker));
//      } else {
//        pushToWorker.setChangeStatusTo(HostStatus.ERROR);
//        messaging.sendMessage("wwd_"+sentTo, Utils.objectToJson(pushToWorker));
//      }
//      messaging.getMessageString("wwd-active", -1); // clean the response
//      pushTask = null;
//      pushThread = null;
//      pushToWorker = null;
//    } else {
//      logger.trace("checkPush: NOT done");
//    }
//  }
}
