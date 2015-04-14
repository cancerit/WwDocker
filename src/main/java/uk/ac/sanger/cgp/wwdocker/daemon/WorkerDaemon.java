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

import com.rabbitmq.client.Channel;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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
  PropertiesConfiguration config;
  Messaging messaging;
  
  public WorkerDaemon(PropertiesConfiguration config, Messaging rmq) {
    this.config = config;
    this.messaging = rmq;
  }
  
  public void run(Boolean testMode) throws IOException, InterruptedException, ConfigurationException {
    WorkerResources hr = new WorkerResources();
    logger.debug(Utils.objectToJson(hr));
    
    File thisConfig = new File("/opt/remote.cfg");
    File thisJar = Utils.thisJarFile();
    
    Thread dockerThread = null;
    
    HostStatus currentState = HostStatus.CLEAN; // if running must be clean or in some processing state
    // build a local WorkerState
    WorkerState thisState = new WorkerState(thisJar, thisConfig);
    WorkerState requiredState = null;
    while (true) {
      // are there any messages?
      List<String> messages = messaging.getMessageStrings("wwd_"+thisState.getResource().getHostName(), 1000);
      if(messages.size() > 0) {
        requiredState = (WorkerState) Utils.jsonToObject(messages.get(0), WorkerState.class);
        thisState = new WorkerState(thisJar, thisConfig); // update the host info
        currentState = determineState(requiredState, dockerThread, thisState);
        thisState.setStatus(currentState);
        messaging.sendMessage("wwd-register", Utils.objectToJson(thisState));
      }
      else {
        // we still want to keep track of the state even if no messages
        currentState = determineState(requiredState, dockerThread, thisState);
        thisState.setStatus(currentState);
      }
      
      
      Thread.sleep(1000);
    }
  }
  
  private static HostStatus determineState(WorkerState requiredState, Thread dockerThread, WorkerState currentState) {
    
    /**
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
    }
    if(hs == HostStatus.CLEAN && requiredState != null && !requiredState.equals(currentState)) {
      logger.fatal("State incompatible with next workflow execution, shutting down cleanly.");
      System.exit(0); // I will be re-provisioned as I am not running
    }
    if(hs == HostStatus.RUNNING) {
      if(!dockerThread.isAlive()) {
        // check for errors and state changes
        hs = HostStatus.DONE; 
        //hs = HostStatus.ERROR; 
        throw new RuntimeException("TODO: Need to determine if ended thread is successful or failed");
      }
    } else if(hs == HostStatus.DONE || hs == HostStatus.ERROR || hs == HostStatus.RECYCLE) {
      throw new RuntimeException("TODO: how should this be handled?");
    }
    logger.info("HostStatus: " + hs);
    
    return hs;
  }
}
