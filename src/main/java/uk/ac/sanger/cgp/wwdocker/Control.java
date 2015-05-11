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
  private static final String USAGE = "\nThe following are valid usage patterns:"
    + "\n\n\tconfig.cfg PRIMARY"
    + "\n\t\t- Starts the 'head' node daemon which provisions and monitors workers"
    + "\n\n\tconfig.cfg PRIMARY KILLALL"
    + "\n\t\t- Issues KILL message to all hosts listed in the workers.cfg file"
    + "\n\n\tconfig.cfg ERRORS /some/path [hostname]"
    + "\n\t\t- Gets and expands logs from the *.ERRORLOG queue";
  
  public static void main(String[] argv) throws Exception {
    int exitCode = 0;
    if(argv.length < 2) {
      System.err.println(USAGE);
      logger.fatal(USAGE);
      System.exit(1);
    }
    
    String configPath = argv[0];
    String executionPath = argv[1];
    
    String modeOrPath = null;
    if(argv.length == 3) {
      modeOrPath = argv[2];
    }
    try {
      PropertiesConfiguration config = Config.loadConfig(configPath);
      Messaging rmq = new Messaging(config);
      if(executionPath.equalsIgnoreCase("errors")) {
        if(modeOrPath == null) {
          logger.fatal(USAGE);
          System.err.println(USAGE);
          System.exit(1);
        }
        ErrorLogs.getLog(config, rmq, modeOrPath);
      }
      else {
        Daemon runme = new DaemonFactory().getDaemon(executionPath, config, rmq);
        runme.run(modeOrPath);
      }
    }
    catch(Exception e) {
      logger.fatal("Unrecoverable error", e);
      exitCode = 1;
    }
    System.exit(exitCode);
  }
}
