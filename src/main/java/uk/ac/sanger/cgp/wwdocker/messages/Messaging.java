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
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.ac.sanger.cgp.wwdocker.actions.Utils;
import uk.ac.sanger.cgp.wwdocker.beans.WorkerState;

/**
 *
 * @author kr2
 */
public class Messaging {

  private static final Logger logger = LogManager.getLogger();

  BaseConfiguration config;
  Connection connectionSend;
  Connection connectionRcv;

  Map<String, Channel> channels = new HashMap();
  Map<String, QueueingConsumer> consumers = new HashMap();

  public Messaging(BaseConfiguration config) {
    this.config = config;
    getRmqConnection(config);
  }

  public void sendMessage(String queue, String message) throws IOException, InterruptedException {
    Channel channel = connectionSend.createChannel();
    channel.queueDeclare(queue, false, false, false, null);
    channel.basicPublish("", queue, null, message.getBytes());
    channel.close();
  }
  
  public List<String> getMessageStrings(String queue, long wait) throws IOException, InterruptedException {
    List<String> responses = new ArrayList();
    Channel channel = connectionRcv.createChannel();
    channel.queueDeclare(queue, false, false, false, null);
    QueueingConsumer consumer = new QueueingConsumer(channel);
    channel.basicConsume(queue, true, consumer);
    while (true) {
      QueueingConsumer.Delivery delivery = consumer.nextDelivery(wait);
      if(delivery == null) {
        break;
      }
      String message = new String(delivery.getBody());
      logger.info(queue + " recieved: " + message);
      responses.add(message);
    }
    channel.close();
    if(responses.size() == 0) {
      logger.info(queue + " no messages");
    }
    return responses;
  }

  protected void getRmqConnection(BaseConfiguration config) {
    ConnectionFactory factory = new ConnectionFactory();
    factory.setHost(config.getString("rabbit_host"));
    factory.setPort(config.getInt("rabbit_port", 5672));
    factory.setUsername(config.getString("rabbit_user"));
    factory.setPassword(config.getString("rabbit_pw"));
    logger.debug(factory.toString());
    try {
      connectionSend = factory.newConnection();
      connectionRcv = factory.newConnection();
    } catch (IOException e) {
      throw new RuntimeException(e.toString(), e);
    }
  }
  
  public String toString() {
    StringBuilder result = new StringBuilder();
    String NEW_LINE = System.getProperty("line.separator");
    result.append(this.getClass().getName() + " Object {" + NEW_LINE);
    result.append(" channels: " + channels + NEW_LINE);
    result.append(" consumers: " + consumers + NEW_LINE);
    result.append(" config: " + config + NEW_LINE);
    result.append(" connectionSend: " + connectionSend + NEW_LINE);
    result.append(" connectionRcv: " + connectionRcv + NEW_LINE);
    result.append("}");
    return result.toString();
  }
}
