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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  /**
   * Sends a message to the specified queue
   * @param queue
   * @param message
   * @throws IOException
   * @throws InterruptedException 
   */
  public void sendMessage(String queue, String message) throws IOException, InterruptedException {
    Channel channel = connectionSend.createChannel();
    channel.queueDeclare(queue, false, false, false, null);
    channel.basicPublish("", queue, null, message.getBytes());
    logger.info(queue + " sent: " + message);
    channel.close();
  }
  
    public void sendMessages(String queue, List<String> messages) throws IOException, InterruptedException {
    Channel channel = connectionSend.createChannel();
    channel.queueDeclare(queue, false, false, false, null);
    for(String m : messages) {
      channel.basicPublish("", queue, null, m.getBytes());
      logger.info(queue + " sent: " + m);
    }
    channel.close();
  }
  
  /**
   * Gets a single message from a queue, ideal for getting an item of work.
   * @param queue
   * @param wait
   * @return A JSON string representing an object, you need to know what type of object the queue will return and handle this outside of here
   * @throws IOException
   * @throws InterruptedException 
   */
  public String getMessageString(String queue, long wait) throws IOException, InterruptedException {
    String message = null;
    Channel channel = connectionRcv.createChannel();
    channel.queueDeclare(queue, false, false, false, null);
    QueueingConsumer consumer = new QueueingConsumer(channel);
    channel.basicConsume(queue, true, consumer);
    QueueingConsumer.Delivery delivery;
    if(wait == -1) {
      delivery = consumer.nextDelivery(); // will block until response
    }
    else {
      delivery = consumer.nextDelivery(wait);
    }
    if(delivery != null) {
      message = new String(delivery.getBody());
      logger.info(queue + " recieved: " + message);
    }
    channel.close();
    return message;
  }
  
  public WorkerState getHostStatus(String host, String message) throws IOException, InterruptedException {
    boolean cleanResponse = false;
    getMessageStrings("wwd-active", 50); // first clean the queue
    sendMessage("wwd_"+host, message);
    String response = getMessageString("wwd-active", -1);
    WorkerState ws = (WorkerState) Utils.jsonToObject(response, WorkerState.class);
    return ws;
  }
  
  public boolean queryGaveResponse(String queryQueue, String responseQueue, String query, long wait) throws IOException, InterruptedException {
    boolean response = false;
    this.sendMessage(queryQueue, query);
    if(getMessageString(responseQueue, wait) != null) {
      response = true;
    }
    else {
      // clean up queue we sent the query
      getMessageStrings(queryQueue, 10);
    }
    return response;
  }
  
  public List<String> messagesRequeue(String queue) throws IOException, InterruptedException {
    List<String> messages = getMessageStrings(queue, 500);
    sendMessages(queue, messages);
    return messages;
  }
  
  public boolean messageSubstrPresent(String queue, String substr) throws IOException, InterruptedException {
    List<String> messages = messagesRequeue(queue);
    boolean found = false;
    for(String m : messages) {
      if(m.contains(substr)) {
        found = true;
        break;
      }
    }
    return found;
  }
  
  public void removeFromQueue(String queue, String messageToRemove) throws IOException, InterruptedException {
    List<String> messages = getMessageStrings(queue, 500);
    List<String> requeue = new ArrayList();
    for(String m : messages ) {
      if(!m.equals(messageToRemove)) {
        requeue.add(m);
      }
    }
    sendMessages(queue, requeue);
  }
  
  public String substrRemoveFromQueue(String queue, String substr) {
    try {
      return substrRemoveFromQueue(queue, substr, 500);
    }
    catch (Exception e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }
  
  public String substrRemoveFromQueue(String queue, String substr, long wait) throws IOException, InterruptedException {
    String removed = null;
    List<String> messages = getMessageStrings(queue, wait);
    List<String> requeue = new ArrayList();
    for(String m : messages ) {
      if(m.contains(substr)) {
        if(removed != null) {
          sendMessages(queue, messages);
          throw new RuntimeException("Substring '"+ substr +"' is not unique in the queue '"+queue+"', all messages requeued");
        }
        removed = m;
      } else {
        requeue.add(m);
      }
    }
    sendMessages(queue, requeue);
    return removed;
  }
  
  /**
   * Gets all the messages in a queue, best for queues which receive status updates.
   * @param queue
   * @param wait
   * @return List of JSON strings representing objects, you need to know what type of object the queue will return and handle this outside of here
   * @throws IOException
   * @throws InterruptedException 
   */
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
