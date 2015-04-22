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
package uk.ac.sanger.cgp.wwdocker.callable;

import com.jcraft.jsch.Session;
import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.ac.sanger.cgp.wwdocker.actions.Local;
import uk.ac.sanger.cgp.wwdocker.actions.Remote;

/**
 *
 * @author kr2
 */
public class PullWork implements Callable<Integer> {
  private static final Logger logger = LogManager.getLogger();
  private Thread t;
  private String threadName;
  private BaseConfiguration config;
  private String host;
  private List<File> toPull;
  private Map<String,String> envs;
  
   
  public PullWork(String threadName, BaseConfiguration config, String host, List<File> toPull, Map<String,String> envs) {
    this.threadName = threadName;
    this.config = config;
    this.host = host;
    this.toPull = toPull;
    this.envs = envs;
    System.out.println("Creating " + threadName);
  }

  public Integer call() {
    Integer result = new Integer(-1);
    logger.info("Running " + threadName);
    
    Session ssh = Remote.getSession(config, host);
    
    if(true) {
      throw new RuntimeException("ah nuts, I don't know what to do yet");
    }
    
    Remote.closeSsh(ssh);
    
    result++;
    
    logger.info("Thread " + threadName + " exiting.");
    return result;
  }
}
