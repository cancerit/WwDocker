/**
 * Copyright (c) 2015 Genome Research Ltd.
 *
 * Author: Cancer Genome Project cgpit@sanger.ac.uk
 *
 * This file is part of WwDocker.
 *
 * WwDocker is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
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
 * 1. The usage of a range of years within a copyright statement contained
 * within this distribution should be interpreted as being equivalent to a list
 * of years including the first and last year specified and all consecutive
 * years between them. For example, a copyright statement that reads 'Copyright
 * (c) 2005, 2007- 2009, 2011-2012' should be interpreted as being identical to
 * a statement that reads 'Copyright (c) 2005, 2007, 2008, 2009, 2011, 2012' and
 * a copyright statement that reads "Copyright (c) 2005-2012' should be
 * interpreted as being identical to a statement that reads 'Copyright (c) 2005,
 * 2006, 2007, 2008, 2009, 2010, 2011, 2012'."
 */
package uk.ac.sanger.cgp.wwdocker.messages;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.QueueingConsumer;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.ac.sanger.cgp.wwdocker.actions.Utils;
import uk.ac.sanger.cgp.wwdocker.beans.WorkerState;
import uk.ac.sanger.cgp.wwdocker.beans.WorkerResources;

/**
 *
 * @author kr2
 */
public class Produce {
  private static final Logger logger = LogManager.getLogger();
  
  public static Map<String, WorkerState> activeHosts(BaseConfiguration config, Channel channel, WorkerState provState) throws IOException, InterruptedException {
    Map<String, WorkerState> hosts = new HashMap();
    String exchange = config.getString("queue_active");
    String excId = "[EX: "+exchange+"] ";
    
    channel.exchangeDeclare(exchange, "direct");
    
    logger.trace(excId + "send: "+ Utils.objectToJson(provState));
    
    channel.basicPublish(exchange, "reportIn", null, Utils.objectToJson(provState).getBytes());
    
    
    //// END OF SEND
    
    // we listen on a different queue though
    
//    int i=0;
//    while(true){if(i++ == 1000) { break; } Thread.sleep(1000);};
    
    String queueName = config.getString("queue_register");
    channel.queueBind(queueName, exchange, "");
    
    QueueingConsumer consumer = new QueueingConsumer(channel);
    channel.basicConsume(config.getString("queue_register"), true, consumer);
    
    long deliveryTimeout = config.getLong("queue_wait");
    logger.trace(excId + "poll responses");
    Thread.sleep(deliveryTimeout); // sleep before the first event
    while (true) {
      QueueingConsumer.Delivery delivery = consumer.nextDelivery(deliveryTimeout);
      if(delivery == null) {
        logger.trace(excId + "no more responses");
        break;
      }
      String response = new String(delivery.getBody());
      logger.trace(excId + "recieved: " + response);
      
      WorkerState ps = (WorkerState) Utils.jsonToObject(response, WorkerState.class);
      hosts.put(ps.getResource().getHostName(), ps);
      
    }
    return hosts;
  }
  
   public static void sendMessage(BaseConfiguration config, Channel channel, String queue, String message) throws IOException, InterruptedException {
    channel.queueDeclare(queue, false, false, false, null);
    channel.basicPublish("", queue, null, message.getBytes());
    logger.trace("[Q: "+queue+"] send: "+message);
  }
}
