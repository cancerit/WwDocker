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

import com.rabbitmq.client.AMQP.BasicProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.QueueingConsumer;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.io.FileUtils;
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
  private Connection connectionSend;
  private Connection connectionRcv;

  Map<String, Channel> channels = new HashMap();
  Map<String, QueueingConsumer> consumers = new HashMap();

  public Messaging(BaseConfiguration config) {
    this.config = config;
    getRmqConnection(config);
  }
  
  public Connection getSendConn() {
    return connectionSend;
  }
  
  public Connection getRcvConn() {
    return connectionRcv;
  }
  
  public void sendMessage(String queue, Object in) throws IOException, InterruptedException {
    sendMessage(queue, in, null);
  }

  /**
   * Sends a message to the specified queue
   * @param queue
   * @param message
   * @param host
   * @throws IOException
   * @throws InterruptedException 
   */
  public void sendMessage(String queue, Object in, String host) throws IOException, InterruptedException {
    String message;
    BasicProperties mProp = null;
    if(in.getClass().equals(String.class)) {
      message = (String) in;
    } else {
      message = Utils.objectToJson(in);
      if(in.getClass().equals(WorkerState.class)) {
        host = ((WorkerState) in).getResource().getHostName();
      }
    }
    if(host != null) {
      Map<String, Object> headers =  new HashMap();
      headers.put("host", host);
      mProp = new BasicProperties.Builder().headers(headers).build();
    }
    
    Channel channel = connectionSend.createChannel();
    channel.queueDeclare(queue, true, false, false, null);
    channel.basicPublish("", queue, mProp, message.getBytes());
    logger.info(queue + " sent: " + message);
    channel.close();
  }
  
  public void sendFile(String queue, String host, File f) throws IOException, InterruptedException {
    Map<String, Object> headers =  new HashMap();
    headers.put("host", host);
    BasicProperties mProp = new BasicProperties.Builder().headers(headers).build();
    Channel channel = connectionSend.createChannel();
    channel.queueDeclare(queue, true, false, false, null);
    channel.basicPublish("", queue, mProp, Files.readAllBytes(f.toPath()));
    logger.info(queue + " file: " + f.getAbsolutePath());
    channel.close();
  }
  
  public void cleanQueue(String queue, String match)  throws IOException, InterruptedException {
    Channel channel = connectionRcv.createChannel();
    channel.queueDeclare(queue, true, false, false, null);
    
    QueueingConsumer consumer = new QueueingConsumer(channel);
    channel.basicConsume(queue, false, consumer);
    QueueingConsumer.Delivery delivery = consumer.nextDelivery(1000);
    Set seen = new HashSet();
    while(delivery != null) {
      String body = new String(delivery.getBody());
      if(seen.contains(body)) {
        break;
      }
      if(body.contains(match)) {
        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
      }
      else {
        channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
        seen.add(body);
      }
      delivery = consumer.nextDelivery(1000);
    }
    channel.close();
  }
  
  public List<File> getFiles(String queue, Path outFolder, boolean ack) throws IOException, InterruptedException {
    List files = new ArrayList();
    Channel channel = connectionRcv.createChannel();
    channel.queueDeclare(queue, true, false, false, null);

    QueueingConsumer consumer = new QueueingConsumer(channel);
    channel.basicConsume(queue, false, consumer);
    QueueingConsumer.Delivery delivery = consumer.nextDelivery(1000);
    
    Set seen = new HashSet();
    
    while(delivery != null) {
      String host = delivery.getProperties().getHeaders().get("host").toString();
      File outTo = Paths.get(outFolder.toString(), host + ".tar.gz").toFile();
      FileUtils.writeByteArrayToFile(outTo, delivery.getBody());
      if(ack) {
        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
      }
      else {
        channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
      }
      if(seen.contains(delivery.getProperties().getHeaders().get("host"))) {
        break;
      }
      seen.add(delivery.getProperties().getHeaders().get("host"));
      files.add(outTo);
      logger.info(queue + " retrieved: " + outTo.getAbsolutePath());
      delivery = consumer.nextDelivery(1000);
    }
    logger.warn("getFiles done");
    channel.close();
    return files;
  }
  
  public WorkerState getWorkerState(String queue, long wait) throws IOException, InterruptedException {
    WorkerState ws = null;
    Channel channel = connectionRcv.createChannel();
    channel.queueDeclare(queue, true, false, false, null);

    QueueingConsumer consumer = new QueueingConsumer(channel);
    channel.basicConsume(queue, false, consumer);
    QueueingConsumer.Delivery delivery;
    if(wait == -1) {
      delivery = consumer.nextDelivery(); // will block until response
    }
    else {
      delivery = consumer.nextDelivery(wait);
    }
    if(delivery != null) {
      String message = new String(delivery.getBody());
      ws = (WorkerState) Utils.jsonToObject(message, WorkerState.class);
      channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
    }
    channel.close();
    return ws;
  }
  
  public Object getMessageObject(String queue, Class objClass, long wait) throws IOException, InterruptedException {
    String message = getMessageString(queue, wait);
    Object result = null;
    if(message != null) {
      result = Utils.jsonToObject(message, objClass);
    }
    return result;
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
    channel.queueDeclare(queue, true, false, false, null);

    QueueingConsumer consumer = new QueueingConsumer(channel);
    channel.basicConsume(queue, false, consumer);
    QueueingConsumer.Delivery delivery;
    if(wait == -1) {
      delivery = consumer.nextDelivery(); // will block until response
    }
    else {
      delivery = consumer.nextDelivery(wait);
    }
    if(delivery != null) {
      message = new String(delivery.getBody());
      channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
      logger.info(queue + " recieved: " + message);
    }
    channel.close();
    return message;
  }
  
  public boolean queryGaveResponse(String queryQueue, String responseQueue, String query, long wait) throws IOException, InterruptedException {
    boolean response = false;
    // clean up queue we send to first
    getMessageStrings(queryQueue, 100);
    this.sendMessage(queryQueue, query);
    if(getMessageString(responseQueue, wait) != null) {
      response = true;
    }
    else {
      // clean up queue we sent the query
      getMessageStrings(queryQueue, 100);
    }
    return response;
  }
  
  public void removeFromStateQueue(String queue, String hostToRemove) throws IOException, InterruptedException {
    Channel channel = connectionRcv.createChannel();
    channel.queueDeclare(queue, true, false, false, null);

    QueueingConsumer consumer = new QueueingConsumer(channel);
    channel.basicConsume(queue, false, consumer);
    boolean foundMyMessage = false;
    QueueingConsumer.Delivery delivery = consumer.nextDelivery(200);
    int maxTries = 1000;
    while(!foundMyMessage && delivery != null && maxTries > 0) {
      // the toString in the middle of this is needed as it is wrapped with another type that can hold 4GB
      if(delivery.getProperties().getHeaders().get("host").toString().equals(hostToRemove)) {
        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
        foundMyMessage = true;
      }
      else {
        channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
      }
      delivery = consumer.nextDelivery(200);
      maxTries--;
    }
    channel.close();
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
    channel.queueDeclare(queue, true, false, false, null);
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
  
  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    String NEW_LINE = System.getProperty("line.separator");
    result.append(this.getClass().getName()).append(" Object {").append(NEW_LINE);
    result.append(" channels: ").append(channels).append(NEW_LINE);
    result.append(" consumers: ").append(consumers).append(NEW_LINE);
    result.append(" config: ").append(config).append(NEW_LINE);
    result.append(" connectionSend: ").append(connectionSend).append(NEW_LINE);
    result.append(" connectionRcv: ").append(connectionRcv).append(NEW_LINE);
    result.append("}");
    return result.toString();
  }
  
  
}
