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
import java.util.concurrent.TimeoutException;
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
  private static final int RETRY_INIT = 300;
  
  public PrimaryDaemon(PropertiesConfiguration config, Messaging rmq) {
    PrimaryDaemon.config = config;
    PrimaryDaemon.messaging = rmq;
  }
  
  @Override
  public void run(String mode) throws IOException, InterruptedException, TimeoutException, ConfigurationException {
    // lots of values that will be used over and over again
    String qPrefix = config.getString("qPrefix");
    File thisJar = Utils.thisJarFile();
    File tmpConf = new File(System.getProperty("java.io.tmpdir") + "/" + qPrefix + ".remote.cfg");
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
    cleanHostQueues(config, hosts);
    
    if(mode != null && mode.equalsIgnoreCase("KILLALL")) {
      killAll(config, hosts, thisJar, tmpConf);
    }
    
    hosts.clear(); // needs to be clean before looping starts
    
    // this holds md5 of this JAR and the config (which lists the workflow code to use)
    WorkerState provState = new WorkerState(thisJar, tmpConf);
    
    int nextRetry = RETRY_INIT;
    
    while(true) {
      addWorkToPend(workManager, config);
      
      // this is a reload as this can change during execution
      hostSet(config, hosts);
      
      for(Map.Entry<String,String> e : hosts.entrySet()) {
        
        String host = e.getKey();
        if(e.getValue().equals("KILL")) {
          provState.setChangeStatusTo(HostStatus.KILL);
          messaging.sendMessage(qPrefix.concat(".").concat(host), Utils.objectToJson(provState));
          hosts.replace(host, "DELETE");
          continue;
        }
        
        provState.setChangeStatusTo(HostStatus.CHECKIN);
        provState.setReplyToQueue(qPrefix.concat(".ACTIVE"));
        
        
        if(e.getValue().equals("TO_PROVISION")
            || ( (e.getValue().equals("CURRENT") || e.getValue().equals("RETRY") )  && nextRetry == 0)
          ) {
          if(!messaging.queryGaveResponse(qPrefix.concat(".").concat(host), provState.getReplyToQueue(), Utils.objectToJson(provState), 15000)) {
            // no response from host... but is it still up
            // see if docker is running before reprovision
            Session hostSession = Remote.getSession(config, host);
            boolean dockerRunning = Remote.dockerRunning(hostSession, hostSession.getUserName());
            boolean workerRunning = Remote.workerRunning(hostSession, hostSession.getUserName());
            Remote.closeSsh(hostSession);
            if(dockerRunning || workerRunning) {
              logger.trace("Retry host later: " + host);
              hosts.replace(host, "RETRY");
              continue;
            }

            logger.info("No response from host '".concat(host).concat("' (re)provisioning..."));
            if(!workManager.provisionHost(host, PrimaryDaemon.config, thisJar, tmpConf, mode, envs)) {
              hosts.replace(host, "BROKEN");
              messaging.removeFromStateQueue(qPrefix.concat(".").concat("BROKEN"), host); // just incase it's already there
              messaging.sendMessage(qPrefix.concat(".").concat("BROKEN"), "Failed to provision", host);
              break;
            }
          }
          if(!e.getValue().equals("CURRENT")) {
            hosts.replace(host, "CURRENT");
            break; // so we start some work on this host before provisioning more
          }
        }
      }
      // we need a little sleep here or we'll kill the queues
      Thread.sleep(1000);
      if(nextRetry == 0) {
        nextRetry = RETRY_INIT;
      }
      else {
        nextRetry--;
      }
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
  
  private void addWorkToPend(Workflow workManager, BaseConfiguration config) throws IOException, InterruptedException, TimeoutException {
    // send all work into the wwd_PEND queue, data can be added during execution, this ensures duplicates don't occur
    List<File> iniFiles = Utils.getWorkInis(config);
    if(iniFiles.isEmpty()) {
      return;
    }
    
    // get all of the existing iniFiles so we can generate a uniq list
    String qPrefix = config.getString("qPrefix");
    List<String> existing = messaging.getMessageStrings(qPrefix.concat(".PEND"), 500);
    Map<String, WorkflowIni> allInis= new HashMap();
    for (String m : existing) {
      WorkflowIni iniFile = (WorkflowIni) Utils.jsonToObject(m, WorkflowIni.class);
      allInis.put(iniFile.getIniFile().getName(), iniFile);
    }
    for(File iniFile : iniFiles) {
      if(!allInis.containsKey(iniFile.getName())) {
        WorkflowIni newIni = new WorkflowIni(iniFile);
        newIni.setLogSearchCmd(workManager.getFindLogsCmds());
        allInis.put(iniFile.getName(), newIni);
      }
    }

    Iterator itr = allInis.values().iterator();
    while(itr.hasNext()) {
      messaging.sendMessage(qPrefix.concat(".PEND"), (WorkflowIni)itr.next());
    }
    
    workManager.iniUpdate(iniFiles, config, HostStatus.PEND);
  }
    
  private void killAll(BaseConfiguration config, Map<String,String> hosts, File thisJar, File thisConf) throws IOException, InterruptedException, TimeoutException {
    WorkerState killState = new WorkerState(thisJar, thisConf);
    killState.setChangeStatusTo(HostStatus.KILL);
    String killJson = Utils.objectToJson(killState);
    String qPrefix = config.getString("qPrefix");
    for(Map.Entry<String,String> e : hosts.entrySet()) {
      messaging.sendMessage(qPrefix.concat(".").concat(e.getKey()), killJson);
    }
    logger.fatal("All hosts shutting down as requested... exiting");
    System.exit(0);
  }
  
  private void cleanHostQueues(BaseConfiguration config, Map<String,String> hosts) throws IOException, InterruptedException {
    String qPrefix = config.getString("qPrefix");
    for(Map.Entry<String,String> e : hosts.entrySet()) {
      messaging.getMessageStrings(qPrefix.concat(".").concat(e.getKey()), 50);
    }
  }
  
//  private static final ExecutorService pushExecutor = Executors.newSingleThreadExecutor();
//  private static FutureTask<Integer> pushTask = null;
//  private static FutureTask<Integer> pullTask = null;
//  private static PushWork pushThread = null;
//  private static PullWork pullThread = null;
//  private static WorkerState pushToWorker = null;
//  private static WorkerState pullFromWorker = null;
//
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
