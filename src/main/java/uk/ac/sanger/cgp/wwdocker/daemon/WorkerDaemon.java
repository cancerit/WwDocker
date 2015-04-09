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
import com.rabbitmq.client.QueueingConsumer;
import java.io.File;
import java.io.IOException;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.ac.sanger.cgp.wwdocker.actions.Utils;
import uk.ac.sanger.cgp.wwdocker.beans.WorkerState;
import uk.ac.sanger.cgp.wwdocker.beans.WorkerResources;
import uk.ac.sanger.cgp.wwdocker.enums.HostStatus;
import uk.ac.sanger.cgp.wwdocker.interfaces.Daemon;
import uk.ac.sanger.cgp.wwdocker.messages.Produce;

/**
 *
 * @author kr2
 */
public class WorkerDaemon implements Daemon {
  private static final Logger logger = LogManager.getLogger();
  PropertiesConfiguration config;
  Channel channel;
  
  public WorkerDaemon(PropertiesConfiguration config, Channel channel) {
    this.config = config;
    this.channel = channel;
  }
  
  public void run() throws IOException, InterruptedException, ConfigurationException {
    WorkerResources hr = new WorkerResources();
    logger.debug(Utils.objectToJson(hr));
    
    String exchange = config.getString("queue_active");
    channel.exchangeDeclare(exchange, "direct");
    String queueName = channel.queueDeclare().getQueue();
    channel.queueBind(queueName, exchange, "reportIn");
    
    logger.info("Waiting for messages from primary:");

    QueueingConsumer consumer = new QueueingConsumer(channel);
    channel.basicConsume(queueName, true, consumer);
    
    while (true) {
      QueueingConsumer.Delivery delivery = consumer.nextDelivery();
      String message = new String(delivery.getBody());
      logger.trace("Recieved: " + message);
      WorkerState requiredState = (WorkerState) Utils.jsonToObject(message, WorkerState.class);
      // build a local WorkerState

      File thisConfig = new File(config.getString("optDir") + "/remote.cfg");
      File thisJar = Utils.thisJarFile();
      WorkerState thisState = new WorkerState(thisJar, thisConfig);
      
      if(thisState.equals(requiredState)) {
        thisState.setStatus(HostStatus.CLEAN);
      }
      else {
        thisState.setStatus(HostStatus.RAW);
      }

      if(thisState.getStatus() == HostStatus.RAW) {
        logger.info("State incompatible with next workflow execution, shutting down cleanly.");
        System.exit(0); // I will be re-provisioned
      }
      
      Produce.sendMessage(config, channel, config.getString("queue_register"), Utils.objectToJson(thisState));
      
    }
  }
}
