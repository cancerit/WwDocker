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

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.ac.sanger.cgp.wwdocker.callable.Docker;
import uk.ac.sanger.cgp.wwdocker.actions.Utils;
import uk.ac.sanger.cgp.wwdocker.beans.WorkerState;
import uk.ac.sanger.cgp.wwdocker.beans.WorkerResources;
import uk.ac.sanger.cgp.wwdocker.enums.HostStatus;
import uk.ac.sanger.cgp.wwdocker.interfaces.Daemon;
import uk.ac.sanger.cgp.wwdocker.messages.Messaging;

/**
 *
 * @author kr2
 */
public class WorkerDaemon implements Daemon {
  private static final Logger logger = LogManager.getLogger();
  private static PropertiesConfiguration config;
  private static Messaging messaging;
  private static Docker dockerThread = null;
  private static ExecutorService executor = null;
  private static FutureTask<Integer> futureTask = null;
  
  public WorkerDaemon(PropertiesConfiguration config, Messaging rmq) {
    WorkerDaemon.config = config;
    WorkerDaemon.messaging = rmq;
  }
  
  @Override
  public void run(String mode) throws IOException, InterruptedException, ConfigurationException {
    WorkerResources hr = new WorkerResources();
    logger.debug(Utils.objectToJson(hr));
    
    File thisConfig = new File("/opt/remote.cfg");
    File thisJar = Utils.thisJarFile();
    String remoteDatadir = config.getString("datastoreDir");
    
    // build a local WorkerState
    WorkerState thisState = new WorkerState(thisJar, thisConfig);
    WorkerState requiredState;
    String hostName = thisState.getResource().getHostName();
    while (true) {
      // are there any messages?
      String message = messaging.getMessageString("wwd_"+hostName, -1);
      thisState.getResource().init(); // update the resource info
      requiredState = (WorkerState) Utils.jsonToObject(message, WorkerState.class);
      determineState(requiredState, thisState);
      messaging.sendMessage("wwd-active", Utils.objectToJson(thisState));
      
      if(requiredState.getChangeStatusTo() != null && requiredState.getChangeStatusTo().equals(HostStatus.RUNNING) && dockerThread == null) {
        logger.debug(thisState.toString());
        String iniFileName = thisState.getWorkflowIni().getName(); 
        File workIni = new File(remoteDatadir.concat("/").concat(iniFileName));
        dockerThread = new Docker(iniFileName, workIni);
        futureTask = new FutureTask<>(dockerThread);
        executor = Executors.newSingleThreadExecutor();
        executor.execute(futureTask);
      }
    }
  }
  
  private static void determineState(WorkerState requiredState, WorkerState currentState) {
    
    /*
     * Progression of states as follows
     * CLEAN -> RUNNING
     * RUNNING -> ERROR/DONE
     * DONE -> RECYCLE
     * ERROR -> ??
     * 
     * Only CLEAN is affected by requiredState
     */
    
    
    HostStatus hs = currentState.getStatus();
    if(hs == null) {
      hs = HostStatus.CLEAN;
      currentState.setStatus(hs);
    }
    if(requiredState != null && requiredState.getChangeStatusTo() != null && requiredState.getChangeStatusTo().equals(HostStatus.KILL)) {
      messaging.substrRemoveFromQueue("wwd_CLEAN", currentState.getResource().getHostName());
      logger.fatal("FORCED SHUTDOWN...");
      System.exit(0); // I will be re-provisioned as I am not running
    }
    
    if(requiredState != null && requiredState.getChangeStatusTo() != null) {
      hs = requiredState.getChangeStatusTo();
      currentState.setStatus(hs);
      if(requiredState.getWorkflowIni() != null) {
        currentState.setWorkflowIni(requiredState.getWorkflowIni());
      }
      return;
    }
    
    if(hs == HostStatus.CLEAN) {
      if(requiredState != null && !requiredState.equals(currentState)) {
        messaging.substrRemoveFromQueue("wwd_CLEAN", currentState.getResource().getHostName());
        logger.fatal("State incompatible with next workflow execution, shutting down cleanly.");
        System.exit(0); // I will be re-provisioned as I am not running
      }
    }
    else if(hs == HostStatus.DONE || hs == HostStatus.ERROR || hs == HostStatus.RECEIVE) {
      // lots of sleeping until I'm told to change or shutdown
      try {
        Thread.sleep(50); // can't sleep too long as primary will get worried
      } catch (InterruptedException e) {
        logger.fatal(e.getMessage(), e);
        throw new RuntimeException(e.getMessage(), e);
      }
    }
    else if(hs == HostStatus.RUNNING) {
      if(futureTask != null && futureTask.isDone()) {
        try {
          if(futureTask.isDone()) {
            int dockerExitCode = futureTask.get();
            
            if(dockerExitCode == 0) {
              hs = HostStatus.DONE;
            }
            else {
              hs = HostStatus.ERROR;
            }
            currentState.setStatus(hs);
            
            //Send to queue for persistance, only on change though
            String json = Utils.objectToJson(currentState);
            messaging.removeFromQueue("wwd_RUNNING", json);
            messaging.sendMessage("wwd_"+hs.name(), json);
            
            logger.info("Exit code: "+ futureTask.get());
            
            executor.shutdown();
            
            dockerThread = null;
            executor = null;
            futureTask = null;
          }
        } catch (Exception e) {
          throw new RuntimeException(e.getMessage(), e);
        }
      }
    }  else {
      throw new RuntimeException("TODO: how should this be handled?\n" + currentState.toString());
    }
    currentState.setStatus(hs);
  }
  
}
