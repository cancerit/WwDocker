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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.ac.sanger.cgp.wwdocker.Config;
import uk.ac.sanger.cgp.wwdocker.actions.Local;
import uk.ac.sanger.cgp.wwdocker.callable.Docker;
import uk.ac.sanger.cgp.wwdocker.actions.Utils;
import uk.ac.sanger.cgp.wwdocker.beans.WorkerState;
import uk.ac.sanger.cgp.wwdocker.beans.WorkerResources;
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
    
    Thread shutdownThread = null;
    
    File thisConfig = new File("/opt/remote.cfg");
    File thisJar = Utils.thisJarFile();
    
    // build a local WorkerState
    WorkerState thisState = new WorkerState(thisJar, thisConfig);
    thisState.setStatus(HostStatus.CLEAN);
    String hostName = thisState.getResource().getHostName();
    String qPrefix = config.getString("qPrefix");
    
    // Remove from broken as I'm not anymore if I'm running
    messaging.removeFromStateQueue(qPrefix.concat(".BROKEN"), hostName);
    
    // I'm running so send a message to the CLEAN queue
    messaging.sendMessage(qPrefix.concat(".CLEAN"), thisState);
    String myQueue = qPrefix.concat(".").concat(hostName);
    
    int counter = 30;
    Workflow workflowImp = new WorkflowFactory().getWorkflow(config);
    while (true) {
      Thread.sleep(500); // don't eat cpu
      //Only control messages will be sent directly to the host now
      
      WorkerState recievedState = (WorkerState) messaging.getWorkerState(myQueue, 10);
      thisState.getResource().init();
      
      if(recievedState != null) {
        if(!recievedState.equals(thisState) && thisState.getStatus().equals(HostStatus.CLEAN)) {
          messaging.removeFromStateQueue(qPrefix.concat(".").concat(thisState.getStatus().name()), hostName);
          logger.fatal("Host refresh required, shutting down...");
          System.exit(0);
        }
        if(recievedState.getChangeStatusTo() != null) {
          if(recievedState.getChangeStatusTo().equals(HostStatus.KILL)) {
            messaging.removeFromStateQueue(qPrefix.concat(".").concat(thisState.getStatus().name()), hostName);
            if(thisState.getStatus().equals(HostStatus.ERROR)) {
              messaging.removeFromStateQueue(qPrefix.concat(".").concat("ERRORLOGS"), hostName);
            }
            if(!thisState.getStatus().equals(HostStatus.CLEAN)) {
              if(shutdownThread == null) {
                messaging.sendMessage(qPrefix.concat(".").concat("PEND"), Utils.objectToJson(thisState.getWorkflowIni()));
              }
            }
            logger.fatal("FORCED SHUTDOWN...");
            if(dockerThread != null) {
              Local.execCommand("docker ps | tail -n +2 | cut -d ' ' -f 1 | xargs docker kill", Config.getEnvs(config), true);
              futureTask.cancel(true);
              executor.shutdownNow();
            }
            System.exit(0);
          }
          else if(recievedState.getChangeStatusTo().equals(HostStatus.CHECKIN)) {
            logger.info(recievedState.toString());
            messaging.sendMessage(recievedState.getReplyToQueue(), thisState);
          }
          else if(recievedState.getChangeStatusTo().equals(HostStatus.RUNNING)) {
            // this is only sent if we want to retry the execution of an errored workflow
            throw new RuntimeException("Restart attempted, I don't know how yet");
          }
        }
      }
      
      // then we do the actual work
      if(thisState.getStatus().equals(HostStatus.CLEAN)) {
        
        // clean up any other queues that may have legacy entries
        messaging.removeFromStateQueue(qPrefix.concat(".").concat("DONE"), hostName);
        messaging.removeFromStateQueue(qPrefix.concat(".").concat("ERROR"), hostName);
        messaging.removeFromStateQueue(qPrefix.concat(".").concat("ERRORLOGS"), hostName);
        messaging.removeFromStateQueue(qPrefix.concat(".").concat("RECEIVE"), hostName);
        messaging.removeFromStateQueue(qPrefix.concat(".").concat("RUNNING"), hostName);
        messaging.removeFromStateQueue(qPrefix.concat(".").concat("BROKEN"), hostName);
        
        //We pull data from the wwd_PEND queue
        WorkflowIni workIni = (WorkflowIni) messaging.getMessageObject(qPrefix.concat(".").concat("PEND"), WorkflowIni.class, 10);
        if(workIni == null) {
          continue;
        }
        logger.debug(thisState.toString());
        thisState.setWorkflowIni(workIni);
        shutdownThread = attachWorkIniShutdownHook(thisState.getWorkflowIni(), messaging, qPrefix);
        workflowImp.cleanDockerPath(config); // clean up the workarea
        
        dockerThread = new Docker(workIni, config);
        
        futureTask = new FutureTask<>(dockerThread);
        executor = Executors.newSingleThreadExecutor();
        executor.execute(futureTask);
        // this section saves having to check you've got it right
        messaging.removeFromStateQueue(qPrefix.concat(".").concat(thisState.getStatus().name()), hostName);
        thisState.setStatus(HostStatus.RUNNING);
        messaging.sendMessage(qPrefix.concat(".").concat(thisState.getStatus().name()), thisState);
      }
      else if(thisState.getStatus().equals(HostStatus.RUNNING)) {
        if(futureTask.isDone()) {
          try {
            int dockerExitCode = futureTask.get();
            //Send to queue for persistance, only on change though
            messaging.removeFromStateQueue(qPrefix.concat(".").concat(thisState.getStatus().name()), hostName);
            if(dockerExitCode == 0) {
              thisState.setStatus(HostStatus.DONE);
              messaging.sendMessage(qPrefix.concat(".").concat("UPLOADED"), thisState.getWorkflowIni());
            }
            else {
              if(dockerThread.getLogArchive() != null) {
                messaging.sendFile(qPrefix.concat(".").concat("ERRORLOGS"), hostName, dockerThread.getLogArchive());
              }
              thisState.setStatus(HostStatus.ERROR);
            }
            
            messaging.sendMessage(qPrefix.concat(".").concat(thisState.getStatus().name()), thisState);
            Runtime.getRuntime().removeShutdownHook(shutdownThread);
            shutdownThread = null;
            logger.info("Exit code: "+ futureTask.get());

            executor.shutdown();
            

            dockerThread = null;
            executor = null;
            futureTask = null;
          } catch (InterruptedException | ExecutionException | IOException e) {
            logger.warn(e.getMessage(), e);
            thisState.setStatus(HostStatus.ERROR);
          }
        }
      }
      else if(thisState.getStatus().equals(HostStatus.DONE)) {
         /* if we need to handle working without GNOS access on images
            then we need to change the logic here to wait for a
            state change pushed from the control code */
        messaging.removeFromStateQueue(qPrefix.concat(".").concat(thisState.getStatus().name()), hostName);
        thisState.setStatus(HostStatus.CLEAN);
        thisState.setWorkflowIni(null);
        messaging.sendMessage(qPrefix.concat(".").concat(thisState.getStatus().name()), thisState);
      }
      else if(thisState.getStatus().equals(HostStatus.ERROR)) {
        if(counter == 60) {
          logger.debug("I'm set to error, waiting for directions...");
          counter = 0;
        }
        counter++;
        Thread.sleep(500); // sleep at top too
      }
      else {
        throw new RuntimeException("Don't know what to do yet");
      }
    }
  }
  
  private Thread attachWorkIniShutdownHook(WorkflowIni ini, Messaging messaging, String qPrefix) {
    Thread sdt = new Thread() {
      @Override
      public void run(){
        try {
          messaging.sendMessage(qPrefix.concat(".").concat("PEND"), Utils.objectToJson(ini));
        }
        catch(IOException | InterruptedException e) {
          throw new RuntimeException("Error while executing shutdownHook", e);
        }
      }
    };
    
    Runtime.getRuntime().addShutdownHook(sdt);
    return sdt;
  }
  
}
