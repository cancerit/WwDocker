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

package uk.ac.sanger.cgp.wwdocker;

import org.apache.commons.configuration.PropertiesConfiguration;
import uk.ac.sanger.cgp.wwdocker.factories.DaemonFactory;
import uk.ac.sanger.cgp.wwdocker.interfaces.Daemon;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.ac.sanger.cgp.wwdocker.messages.Messaging;

/**
 *
 * @author kr2
 */
public class Control {
  private static final Logger logger = LogManager.getLogger();
  
  public static void main(String[] argv) throws Exception {
    int exitCode = 0;
    if(argv.length < 2) {
      logger.fatal("Daemon type and configuration file must be supplied");
      System.exit(1);
    }
    
    String daemonType = argv[0];
    String configPath = argv[1];
    String mode = null;
    if(argv.length == 3) {
      mode = argv[2];
    }
    try {
      PropertiesConfiguration config = Config.loadConfig(configPath);
      Messaging rmq = new Messaging(config);
      
      Daemon runme = new DaemonFactory().getDaemon(daemonType, config, rmq);
      runme.run(mode);
    }
    catch(Exception e) {
      logger.fatal("Unrecoverable error", e);
      exitCode = 1;
    }
    System.exit(exitCode);
  }
}
