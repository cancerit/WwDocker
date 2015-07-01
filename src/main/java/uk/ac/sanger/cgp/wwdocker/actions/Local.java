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

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.ac.sanger.cgp.wwdocker.Config;

/**
 *
 * @author kr2
 */
public class Local {
  private static final Logger logger = LogManager.getLogger();
  
  public static int pushFileSetToHost(List<File> sources, String destHost, String destPath, Map envs, Session session, File tmpIn) {
    int exitCode = 0;
    for(File source : sources) {
      int lExit = pushToHost(source.getAbsolutePath(), destHost, destPath, envs, session, tmpIn);
      if(lExit != 0) {
        exitCode = lExit;
        break;
      }
    }
    return exitCode;
  }
  
  public static int pushFileSetToHost(String[] sources, String destHost, String destPath, Map envs, Session session, File tmpIn) {
    int exitCode = 0;
    for(String sourceStr : sources) {
      File source = new File(sourceStr);
      int lExit = pushToHost(source.getAbsolutePath(), destHost, destPath, envs, session, tmpIn);
      if(lExit != 0) {
        exitCode = lExit;
        break;
      }
    }
    return exitCode;
  }
  
  public static int pushToHost(String source, String destHost, String destPath, Map envs, Session session, File tmpIn) {
    String localFile = source;
    int exitCode = -1;
    int pullExit = 0;
    if(source.startsWith("http") || source.startsWith("ftp")) {
      String[] elements = source.split("/");
      String localTmp;
      if(tmpIn != null) {
        localTmp = tmpIn.getAbsolutePath();
      }
      else {
        localTmp = System.getProperty("java.io.tmpdir");
      }
      if(!localTmp.endsWith(System.getProperty("file.separator"))) {
        localTmp = localTmp.concat(System.getProperty("file.separator"));
      }
      localFile = localTmp.concat(elements[elements.length-1]);
      // -z only transfer if modified
      String getCommand = "curl -RLsS"
                            .concat(" -z ").concat(localFile)
                            .concat(" -o ").concat(localFile)
                            .concat(" ").concat(source);
      pullExit = execCommand(getCommand, envs);
    }
    if(pullExit == 0) {
      try {
        exitCode = Remote.fileTo(session, localFile, destPath.concat("/."));
      } catch(JSchException e) {
        throw new RuntimeException("Failure in SSH connection", e);
      }
    }
    else {
      exitCode = pullExit;
    }
    return exitCode;
  }
  
  public static int chmod(File f, String perms) {
    String command = "chmod ".concat(perms).concat(" ").concat(f.getAbsolutePath());
    return execCommand(command);
  }
  
  public static int execCommand(String command) {
    Map<String,String> noEnv = new HashMap();
    return execCommand(command, noEnv, false);
  }
  
  public static int execCommand(String command, Map noEnv) {
    return execCommand(command, noEnv, false);
  }
  
  public static int execCommand(BaseConfiguration config, String command, boolean shellCmd) {
    return execCommand(command, Config.getEnvs(config), shellCmd);
  }
  
  public static int execCommand(String command, Map envs, boolean shellCmd) {
    ProcessBuilder pb;
    if(shellCmd) {
      pb = new ProcessBuilder("/bin/sh", "-c", command);
    }
    else {
      pb = new ProcessBuilder(command.split(" "));
    }
    Map<String, String> pEnv = pb.environment();
    pEnv.putAll(envs);
    logger.info("Executing: " + command);
    int exitCode = -1;
    Process p = null;
    File tempOut = null;
    File tempErr = null;
    try {
      tempOut = File.createTempFile("wwdExec", ".out");
      tempErr = File.createTempFile("wwdExec", ".err");
      pb.redirectOutput(tempOut);
      pb.redirectError(tempErr);
      p = pb.start();
      exitCode = p.waitFor();
    } catch(InterruptedException | IOException e) {
      logger.error(e.getMessage(), e);
    }
    finally {
      if(tempOut != null && tempErr != null) {
        try {
          logger.info(IOUtils.toString(new FileInputStream(tempOut)));
          logger.error(IOUtils.toString(new FileInputStream(tempErr)));
          tempOut.delete();
          tempErr.delete();
        } catch (IOException e) {
          logger.error("Failed to get output from log files");
        }
      }
      if(p != null) {
        p.destroy();
        exitCode = p.exitValue();
      }
    }
    return exitCode;
  }
  
  public static Map<String,String> execCapture(String command, Map envs, boolean shellCmd) {
    Map<String,String> result = new HashMap();
    ProcessBuilder pb;
    if(shellCmd) {
      pb = new ProcessBuilder("/bin/sh", "-c", command);
    }
    else {
      pb = new ProcessBuilder(command.split(" "));
    }
    Map<String, String> pEnv = pb.environment();
    pEnv.putAll(envs);
    logger.info("Executing: " + command);
    int exitCode = -1;
    Process p = null;
    File tempOut = null;
    File tempErr = null;
    try {
      tempOut = File.createTempFile("wwdExec", ".out");
      tempErr = File.createTempFile("wwdExec", ".err");
      pb.redirectOutput(tempOut);
      pb.redirectError(tempErr);
      p = pb.start();
      exitCode = p.waitFor();
    } catch(InterruptedException | IOException e) {
      logger.error(e.getMessage(), e);
    }
    finally {
      if(tempOut != null && tempErr != null) {
        try {
          result.put("stdout", StringUtils.chomp(IOUtils.toString(new FileInputStream(tempOut))));
          result.put("stderr", StringUtils.chomp(IOUtils.toString(new FileInputStream(tempErr))));
          tempOut.delete();
          tempErr.delete();
          logger.info(result.get("stdout"));
          logger.error(result.get("stderr"));
        } catch (IOException e) {
          logger.error("Failed to get output from log files");
        }
      }
      if(p != null) {
        p.destroy();
        exitCode = p.exitValue();
      }
    }
    result.put("exitCode", Integer.toString(exitCode));
    return result;
  }
}

