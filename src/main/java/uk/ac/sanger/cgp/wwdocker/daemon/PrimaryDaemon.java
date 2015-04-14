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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import uk.ac.sanger.cgp.wwdocker.interfaces.Daemon;
import uk.ac.sanger.cgp.wwdocker.messages.Messaging;

/**
 *
 * @author kr2
 */
public class PrimaryDaemon implements Daemon {
  private static final Logger logger = LogManager.getLogger();
  
  PropertiesConfiguration config;
  Messaging messaging;
  String[] optionalEnvs = {"http_proxy", "https_proxy"};
  
  public PrimaryDaemon(PropertiesConfiguration config, Messaging rmq) {
    this.config = config;
    this.messaging = rmq;
  }
  
  public void run(Boolean testMode) throws IOException, InterruptedException, ConfigurationException {
    String basicQueue = config.getString("queue_register");
    
    // lots of values that will be used over and over again
    File jreDist = Utils.expandUserFile(config, "jreDist", true);
    File thisJar = Utils.thisJarFile();
    File tmpConf = new File(System.getProperty("java.io.tmpdir") + "/remote.cfg");
    tmpConf.deleteOnExit(); // contains passwords so cleanup
    config.save(tmpConf.getAbsolutePath()); // done like this so includes are pulled in
    Local.chmod(tmpConf, "go-rwx");
    String remoteWorkflowDir = config.getString("workflowDir");
    String remoteDatastoreDir = config.getString("datastoreDir");
    String localSeqwareJar = config.getString("seqware"); // can be http location
    String localWorkflowZip = config.getString("workflow"); // can be http location
    File remoteSeqwareJar = new File(remoteWorkflowDir.concat("/").concat(localSeqwareJar.replaceAll(".*/", "")));
    File remoteWorkflowZip = new File(remoteWorkflowDir.concat("/").concat(localWorkflowZip.replaceAll(".*/", "")));
    String baseDockerImage = config.getString("baseDockerImage");
    String[] provisionPaths = config.getStringArray("provisionPaths");
    String optDir = "/opt";
    String workerLog = config.getString("log4-worker");
    File localTmp = Utils.expandUserDirPath(config, "primaryLargeTmp", true);
    
    // this holds md5 of this JAR and the config (which lists the workflow code to use)
    WorkerState provState = new WorkerState(thisJar, tmpConf);
    
    Map<String,String> envs = new HashMap();
    for(String env : optionalEnvs) {
      String value = config.getString(env);
      if(value != null) {
        envs.put(env, value);
      }
    }
    
    int counter = 0;
    while(true) {
      counter++;
      /**
       * gets the list of worker hosts which CAN change during runtime
       * There is a way to get the config to load when changed, however it only
       * matters when we loop round so we may as well just rebuild the object
       */ 
      BaseConfiguration workerConf = Config.loadWorkers(config.getString("workerCfg"));

      /**
       * Now we send out a message to all active hosts to find out what there are
       * doing, shutting down the primary doesn't man the workers shutdown
       * this is the advantage of using the MQ to monitor things
       */

      String[] hosts = workerConf.getStringArray("hosts");
      for(String host:hosts) {
        logger.info("query host: " + host);
        messaging.sendMessage("wwd_"+host, Utils.objectToJson(provState));
      }
      
      if(counter == 10) {
        break;
      }
      
      List<String> responses = messaging.getMessageStrings("wwd-register", 5000);
      Map<String, WorkerState> activeHosts = new HashMap();
      for(String r : responses) {
        WorkerState ws = (WorkerState) Utils.jsonToObject(r, WorkerState.class);
        activeHosts.put(ws.getResource().getHostName(), ws);
      }
      

      for(String host:hosts) {
        if (activeHosts.containsKey(host)) {
          WorkerState ws = (WorkerState) activeHosts.get(host);
          // I'm not sure, I guess look at object and see if it's running anything?
          logger.trace(Utils.objectToJson(ws));
          
          switch (ws.getStatus()) {
            case CLEAN:
              logger.info("Host is ready to recieve data files");
              break;
            default:
              logger.info("WHO CARES!");
              break;
          }
        } else {
          logger.info("No response from host '".concat(host).concat("' (re)provisioning..."));
          // no daemon running, or should have shutdown as incompatible state
          Session ssh = Remote.getSession(config, host);

          // clean and setup paths
          if(!testMode) {
            Remote.cleanHost(ssh, provisionPaths);
          }
          Remote.createPaths(ssh, provisionPaths);
          Remote.chmodPaths(ssh, "a+wrx", provisionPaths, true);

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

          Remote.startWorkerDaemon(ssh, thisJar.getName(), testMode);
          ssh.disconnect();
          while(ssh.isConnected()) {
            Thread.sleep(10);
          }
          
          Thread.sleep(8000); // give the worker daemon time to spin up
          /**
           * this may seem odd, but we want to scale up gradually
           * restarting the loop after each provision ensures that we don't
           * spend forever day provisioning and not sending any work out
           */
          break;
        }
      }
    }
  }
}
