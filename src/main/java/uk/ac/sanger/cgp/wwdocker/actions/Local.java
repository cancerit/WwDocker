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

package uk.ac.sanger.cgp.wwdocker.actions;

import java.io.IOException;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author kr2
 */
public class Local {
  private static final Logger logger = LogManager.getLogger();
  
  public static void pushFileSetToHost(String[] sources, String destHost, String destPath) {
    for(String source : sources) {
      pushToHost(source, destHost, destPath);
    }
  }
  
  public static int pushToHost(String source, String destHost, String destPath, String sshUser) {
    String localFile = source;
    int exitCode = -1;
    if(source.startsWith("http") || source.startsWith("ftp")) {
      String[] elements = source.split("/");
      String localTmp = System.getProperty("java.io.tmpdir");
      if(!localTmp.endsWith(System.getProperty("file.separator"))) {
        localTmp = localTmp.concat(System.getProperty("file.separator"));
      }
      localFile = localTmp.concat(elements[elements.length-1]);
      String getCommand = "curl -sS -o ".concat(localFile).concat(" ").concat(source);
      exitCode = execCommand(getCommand);
    }
    // @TODO - this needs to use the SSH aproach as known host type transfer won't be possible
    String pushCommand = "rsync -q ".concat(localFile).concat(" ")
                        .concat(sshUser).concat("@")
                        .concat(destHost).concat(":").concat(destPath).concat("/.");
    exitCode = execCommand(pushCommand);
    return exitCode;
  }
  
  
  private static int execCommand(String command) {
    logger.info("Executing: " + command);
    ProcessBuilder pb = new ProcessBuilder(command.split(" "));
    int exitCode;
    try {
      Process p = pb.start();
      exitCode = p.waitFor();
      if(exitCode != 0) {
        String progErr = IOUtils.toString(p.getErrorStream());
        Utils.logOutput(progErr, Level.ERROR);
        throw new RuntimeException("An error occurred executing: "
                                  + command + "\n\t" + progErr);
      }
      Utils.logOutput(IOUtils.toString(p.getInputStream()), Level.TRACE);
    } catch(InterruptedException e) {
      throw new RuntimeException("Execution of command interrupted: " + command, e);
    } catch(IOException e) {
      throw new RuntimeException("IOException during execution of command: " + command, e);
    }
    return exitCode;
  }
}

