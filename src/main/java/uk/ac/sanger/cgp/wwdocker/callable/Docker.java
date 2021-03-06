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
import java.util.Map;
import java.util.concurrent.Callable;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.ac.sanger.cgp.wwdocker.actions.Local;
import uk.ac.sanger.cgp.wwdocker.beans.WorkflowIni;
import uk.ac.sanger.cgp.wwdocker.factories.WorkflowFactory;
import uk.ac.sanger.cgp.wwdocker.interfaces.Workflow;

/**
 *
 * @author kr2
 */
public class Docker implements Callable<Integer> {
  private static final Logger logger = LogManager.getLogger();
  private Thread t;
  private String threadName;
  private WorkflowIni iniFile;
  private File remoteIni;
  private BaseConfiguration config;
  File logArchive = null;
  private Workflow workManager = null;
   
  public Docker(WorkflowIni iniFile, BaseConfiguration config) {
    this.config = config;
    this.threadName = iniFile.getIniFile().getName();
    this.iniFile = iniFile;
    remoteIni = Paths.get(config.getString("datastoreDir"), threadName).toFile();
    workManager = new WorkflowFactory().getWorkflow(config);
  }

  public Integer call() {
    Integer result = new Integer(-1);
    logger.info("Running " + threadName);
    try {
      FileUtils.writeStringToFile(remoteIni, iniFile.getIniContent(), null);
      
      result = workManager.runDocker(config, remoteIni);
      if(result != 0) {
        // first check the log output for 'Setting workflow-run status to completed for'
        String grepStarted = "'grep -nF \"Created workflow run with SWID: \" /tmp/WwDocker-logs/WwDocker-info.log | tail -n 1 | cut -f 1 -d \";\"'";
        Map<String,String> startedRes = Local.execCapture(grepStarted, null, true);
        
        String grepCompleted = "'grep -nF \"Setting workflow-run status to completed for\" /tmp/WwDocker-logs/WwDocker-info.log | tail -n 1 | cut -f 1 -d \":\"";
        Map<String,String> completeRes = Local.execCapture(grepStarted, null, true);
        
        if(completeRes.get("stdout").length() > 0 && startedRes.get("stdout").length() > 0) {
          int startedLine = Integer.parseInt(startedRes.get("stdout"));
          int completeLine = Integer.parseInt(completeRes.get("stdout"));
          if(completeLine > startedLine) {
            result = 0;
          }
        }
        // we should package the results as defined by the config of this workflow type
        logArchive = packageLogs();
      }
      
    } catch (IOException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
    
    logger.trace("Thread " + threadName + " exiting.");
    logger.trace("Result: " + result);
    return result;
  }
  
  public File getLogArchive() {
    return logArchive;
  }
  
  public File packageLogs() {
    String datastore = config.getString("datastoreDir");
    String includeFile = datastore.concat("/toInclude.lst");
    File logTar = Paths.get(datastore,"logs.tar.gz").toFile();
    if(logTar.exists()) {
      logTar.delete();
    }
    String command = "cd ".concat(datastore)
      .concat("; find *.ini -type f > ") // grab the ini file
      .concat(includeFile)
      .concat("; find oozie-*/generated-scripts/ -type f >> ")
      .concat(includeFile)
      .concat("; find /tmp/WwDocker-logs/ -type f >> ") // will break if log4j output moved
      .concat(includeFile);
    
    for(String c : iniFile.getLogSearchCmds()) {
      command = command.concat(";").concat(c).concat(">>").concat(includeFile);
    }
    command = command.concat(";tar -C ").concat(datastore)
      .concat(" -czf ")
      .concat(logTar.getAbsolutePath())
      .concat(" -T ").concat(includeFile);
    Local.execCommand(config, command, true);
    return logTar;
  }
}
