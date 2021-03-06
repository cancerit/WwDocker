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
package uk.ac.sanger.cgp.wwdocker;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.plexus.util.FileUtils;
import uk.ac.sanger.cgp.wwdocker.actions.Local;
import uk.ac.sanger.cgp.wwdocker.messages.Messaging;

/**
 *
 * @author kr2
 */
public class ErrorLogs {
  private static final Logger logger = LogManager.getLogger();
  
  public static void getLog(PropertiesConfiguration config, Messaging messaging, String outBase) throws IOException {
    List<File> logSets = null;
    try {
      Path basePath = Paths.get(outBase);
      File baseLoc = basePath.toFile();
      if(!baseLoc.exists()) {
        baseLoc.mkdirs();
      }
      logSets = messaging.getFiles(config.getString("qPrefix").concat(".ERRORLOGS"), basePath, false);
    } catch(IOException | InterruptedException e) {
      logger.fatal(e.getMessage(), e);
      System.exit(1);
    }
    if(logSets != null) {
      for(File f : logSets) {
        String host = f.getName().replaceAll("\\.tar\\.gz$", "");
        File d = Paths.get(outBase, host).toFile();
        if(d.exists()) {
          FileUtils.deleteDirectory(d);
        }
        d.mkdirs();
        String command = "tar -C ".concat(d.getAbsolutePath()).concat(" -zxf ").concat(f.getAbsolutePath());
        Local.execCommand(command);
        f.delete();
      }
    }
  }
}
