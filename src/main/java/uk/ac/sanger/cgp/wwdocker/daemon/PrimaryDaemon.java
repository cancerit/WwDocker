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
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.ac.sanger.cgp.wwdocker.actions.Remote;
import uk.ac.sanger.cgp.wwdocker.interfaces.Daemon;
import uk.ac.sanger.cgp.wwdocker.interfaces.HostInfo;
import uk.ac.sanger.cgp.wwdocker.messages.Produce;

/**
 *
 * @author kr2
 */
public class PrimaryDaemon implements Daemon {
  private static final Logger logger = LogManager.getLogger();
  
  BaseConfiguration config;
  Channel channel;
  String[] optionalEnvs = {"http_proxy", "https_proxy"};
  
  public PrimaryDaemon(BaseConfiguration config, Channel channel) {
    this.config = config;
    this.channel = channel;
  }
  
  public void run() throws IOException, InterruptedException {
    String basicQueue = config.getString("queue_register");

    //channel.queueDeclare(basicQueue, false, false, false, null);
    //String message = "Hello World!";
    //channel.basicPublish("", basicQueue, null, message.getBytes());
    //logger.debug(" [x] Sent '" + message + "'");
    
    Map<String, HostInfo> activeHosts = Produce.activeHosts(config, channel);
    
    // check for any messages on www-docker-register
    
    
    Map<String,String> envs = new HashMap();
    for(String env : optionalEnvs) {
      String value = config.getString(env);
      if(value != null) {
        envs.put(env, value);
      }
    }
    String[] hosts = config.getStringArray("hosts");
    for(String host:hosts) {
      if(activeHosts.containsKey(host)) {
        // I'm not sure, I guess look at object and see if it's running anything?
      }
      else {
        // no daemon running
        Session ssh = Remote.getSession(config, host);
        
        ssh.disconnect();
        // setup ssh session, push this JAR, cgf and log4j.ini to the host and start as worker (&)
        // wait for host on wwd-worker-register
      }
//      Session ssh = Remote.getSession(config, host);
//      Remote.createPaths(ssh, config.getStringArray("provisionPaths"));
//      Remote.chmodPaths(ssh, "a+wrx", config.getStringArray("provisionPaths"));
//      Remote.stageDocker(ssh, config.getString("baseDockerImage"));
//      Local.pushToHost(config.getString("seqwareBase"), host, config.getString("workflowDir"), envs, ssh);
//      ssh.disconnect();
    }
  }
}
