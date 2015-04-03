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
import uk.ac.sanger.cgp.wwdocker.factories.HostInfoFactory;
import uk.ac.sanger.cgp.wwdocker.interfaces.HostInfo;

/**
 *
 * @author kr2
 */
public class Produce {
  private static final Logger logger = LogManager.getLogger();
  private static String REQ_ACTIVE = "REPORT IN";
  
  public static Map<String, HostInfo> activeHosts(BaseConfiguration config, Channel channel) throws IOException, InterruptedException {
    Map<String, HostInfo> hosts = new HashMap();
    String exchange = config.getString("queue_active");
    String excId = "["+exchange+"] ";
    
    channel.exchangeDeclare(exchange, "fanout");
    
    logger.trace(excId + "send: "+REQ_ACTIVE);
    
    channel.basicPublish(exchange, "", null, REQ_ACTIVE.getBytes());
    
    String queueName = channel.queueDeclare().getQueue();
    channel.queueBind(queueName, exchange, "");
    
    QueueingConsumer consumer = new QueueingConsumer(channel);
    channel.basicConsume(queueName, true, consumer);
    
    HostInfoFactory hif = new HostInfoFactory();
    int deliveryTimeout = config.getInt("queue_wait");
    logger.trace(excId + "poll responses");
    while (true) {
      QueueingConsumer.Delivery delivery = consumer.nextDelivery(deliveryTimeout);
      if(delivery == null) {
        logger.trace(excId + "no more responses");
        break;
      }
      HostInfo hi = hif.getHostDetailsFromString(new String(delivery.getBody()));
      logger.trace(excId + "recieved: " + hi.toString());
      hosts.put(hi.getHostName(), hi);
    }
    return hosts;
  }
}
