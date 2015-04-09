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
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import uk.ac.sanger.cgp.wwdocker.Config;
import uk.ac.sanger.cgp.wwdocker.actions.Local;
import uk.ac.sanger.cgp.wwdocker.actions.Remote;
import uk.ac.sanger.cgp.wwdocker.actions.Utils;
import uk.ac.sanger.cgp.wwdocker.beans.WorkerResources;
import uk.ac.sanger.cgp.wwdocker.beans.WorkerState;
import uk.ac.sanger.cgp.wwdocker.enums.HostStatus;
import uk.ac.sanger.cgp.wwdocker.interfaces.Daemon;
import uk.ac.sanger.cgp.wwdocker.messages.Produce;

/**
 *
 * @author kr2
 */
public class PrimaryDaemon implements Daemon {
  private static final Logger logger = LogManager.getLogger();
  
  PropertiesConfiguration config;
  Channel channel;
  String[] optionalEnvs = {"http_proxy", "https_proxy"};
  
  public PrimaryDaemon(PropertiesConfiguration config, Channel channel) {
    this.config = config;
    this.channel = channel;
  }
  
  public void run() throws IOException, InterruptedException, ConfigurationException {
    String basicQueue = config.getString("queue_register");
    
    File jreDist = new File(config.getString("jreDist"));
    File thisJar = Utils.thisJarFile();
    File tmpConf = new File(System.getProperty("java.io.tmpdir") + "/remote.cfg");
    config.save(tmpConf.getAbsolutePath()); // done like this so includes are pulled in
    
    // this holds md5 of this JAR and the config (which lists the workflow code to use)
    WorkerState provState = new WorkerState(thisJar, tmpConf);
    
    /**
     * A LOOP WILL BE ADDED HERE MOST LIKELY
     */
    
    /**
     * gets the list of worker hosts which CAN change during runtime
     * There is a way to get the config to load when changed, however it only
     * matters when we loop round so we may as well just rebuild the object
     */ 
    BaseConfiguration workerConf = Config.loadWorkers(config.getString("workerCfg"));
    
    /**
     * Now we send out a message to all active hosts to find out what there are
     * doing, shutting down the primary doesn't man the workers shutdown
     * this is the advantage of using the MQ to monitor things
     */
    //Map<String, HostInfo> activeHosts = new HashMap();
    Map<String, WorkerState> activeHosts = Produce.activeHosts(config, channel, provState);
    
    // check for any messages on www-docker-register
    
    
    Map<String,String> envs = new HashMap();
    for(String env : optionalEnvs) {
      String value = config.getString(env);
      if(value != null) {
        envs.put(env, value);
      }
    }
    
    logger.info("Active Hosts: ".concat(Utils.objectToJson(activeHosts)));
    
    
    //String[] hosts = new String[0];
    String[] hosts = workerConf.getStringArray("hosts");
    for(String host:hosts) {
      if(activeHosts.containsKey(host)) {
        // I'm not sure, I guess look at object and see if it's running anything?
      }
      else {
        logger.info("No response from host '".concat(host).concat("' (re)provisioning..."));
        // no daemon running, or should have shutdown as incompatible state
        Session ssh = Remote.getSession(config, host);
        
        // clean and setup paths
        Remote.cleanHost(ssh, config.getStringArray("provisionPaths"));
        Remote.createPaths(ssh, config.getStringArray("provisionPaths"));
        Remote.chmodPaths(ssh, "a+wrx", config.getStringArray("provisionPaths"), true);
        
        // setup docker and the seqware image
        Remote.stageDocker(ssh, config.getString("baseDockerImage"));
        Local.pushToHost(config.getString("seqwareBase"), host, config.getString("workflowDir"), envs, ssh);
        
        // send the code needed to run the worker daemon, 
        // DON'T send required items after this point as starting the daemon signifies successful setup
        Local.pushToHost(jreDist.getAbsolutePath(), host, config.getString("optDir"), envs, ssh);
        Remote.expandJre(ssh, jreDist);
        Local.pushToHost(thisJar.getAbsolutePath(), host, config.getString("optDir"), envs, ssh);
        Local.pushToHost(config.getString("log4-worker"), host, config.getString("optDir"), envs, ssh);
        Local.pushToHost(tmpConf.getAbsolutePath(), host, config.getString("optDir"), envs, ssh);
        tmpConf.delete();
        Remote.chmodPath(ssh, "go-wrx", config.getString("optDir").concat("/*"), true); // file will have passwords

        Remote.startWorkerDaemon(ssh, thisJar.getName());
        ssh.disconnect();
      }
    }
  }
}
