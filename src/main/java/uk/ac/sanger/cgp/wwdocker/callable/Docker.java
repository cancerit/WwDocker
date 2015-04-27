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

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.Callable;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.ac.sanger.cgp.wwdocker.actions.Local;
import uk.ac.sanger.cgp.wwdocker.beans.WorkflowIni;

/**
 *
 * @author kr2
 */
public class Docker implements Callable<Integer> {
  private static final Logger logger = LogManager.getLogger();
  private static final String baseLogCmd = "cd /cgp/datastore/oozie-* ; find generated-scripts/ -type f > /cgp/datastore/toInclude.lst;";
  private static final String packageLogs = "tar -C /cgp/datastore/oozie-* -czf /cgp/datastore/logs.tar.gz -T /cgp/toInclude.lst";
  private Thread t;
  private String threadName;
  private WorkflowIni iniFile;
  private File remoteIni;
  private BaseConfiguration config;
  File logArchive = null;
   
  public Docker(WorkflowIni iniFile, BaseConfiguration config) {
    this.config = config;
    this.threadName = iniFile.getIniFile().getName();
    this.iniFile = iniFile;
    remoteIni = Paths.get(config.getString("datastoreDir"), threadName).toFile();
  }

  public Integer call() {
    Integer result = new Integer(-1);
    logger.info("Running " + threadName);
    
    try {
      FileUtils.writeStringToFile(remoteIni, iniFile.getIniContent(), null);
      
      result = Local.runDocker(config, remoteIni);
      if(result != 0) {
        // we should package the results as defined by the config of this workflow type
        logArchive = packageLogs();
      }
      
    } catch (IOException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
    
    logger.info("Thread " + threadName + " exiting.");
    return result;
  }
  
  public File getLogArchive() {
    return logArchive;
  }
  
  private File packageLogs() {
    String oozieBase = config.getString("datastoreDir").concat("/oozie-*");
    String includeFile = config.getString("datastoreDir").concat("/toInclude.lst");
    File logArchive = Paths.get(config.getString("datastoreDir"),"logs.tar.gz").toFile();
    
    String command = "cd ".concat(oozieBase)
      .concat("; find generated-scripts/ -type f > ")
      .concat(includeFile).concat(";")
      .concat(iniFile.getLogSearchCmd())
      .concat(">>").concat(includeFile)
      .concat(";tar -C ").concat(oozieBase)
      .concat(" -czf ")
      .concat(logArchive.getAbsolutePath())
      .concat(" -T ").concat(includeFile);
    Local.execCommand(config, command, true);
    return logArchive;
  }
}
