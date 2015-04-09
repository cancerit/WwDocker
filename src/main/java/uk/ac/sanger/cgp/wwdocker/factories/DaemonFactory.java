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

package uk.ac.sanger.cgp.wwdocker.factories;

import com.rabbitmq.client.Channel;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.ac.sanger.cgp.wwdocker.daemon.PrimaryDaemon;
import uk.ac.sanger.cgp.wwdocker.daemon.WorkerDaemon;
import uk.ac.sanger.cgp.wwdocker.interfaces.Daemon;

/**
 *
 * @author kr2
 */
public class DaemonFactory {
  private static final Logger logger = LogManager.getLogger();
  public Daemon getDaemon(String daemonType, PropertiesConfiguration config, Channel channel) {
    if(daemonType.equalsIgnoreCase("PRIMARY")) {
      logger.trace("Creating '"+ daemonType +"' daemon");
      return new PrimaryDaemon(config, channel);
    }
    else if(daemonType.equalsIgnoreCase("WORKER")) {
      logger.trace("Creating '"+ daemonType +"' daemon");
      return new WorkerDaemon(config, channel);
    }
    throw new RuntimeException("Daemon type must be 'primary' or 'worker': " + daemonType);
  }
}
