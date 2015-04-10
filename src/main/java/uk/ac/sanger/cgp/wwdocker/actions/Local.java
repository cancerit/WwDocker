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
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.io.FileUtils;
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
  
  public static int runDocker(BaseConfiguration config) {
    /**
     * docker run --rm -h master -t
     * -v /cgp/datastore:/datastore
     * -v /cgp/Workflow_Bundle_SangerPancancerCgpCnIndelSnvStr_1.0.5.1_SeqWare_1.1.0-alpha.5:/workflow
     * -i seqware/seqware_whitestar_pancancer
     * seqware bundle launch
     * --no-metadata
     * --engine whitestar-parallel
     * --dir /workflow
     * --ini /datastore/testRun.ini
     */
    
    File workflow = new File(config.getString("workflowDir")
                              .concat("/")
                              .concat(config.getString("workflow").replaceAll(".*/", "").replaceAll("\\.zip$", ""))
                            );
    
    String command = "docker run --rm -h master -t";
    command = command.concat(" -v ").concat(config.getString("datastoreDir")).concat(":/datastore");
    command = command.concat(" -v ").concat(config.getString("workflow")).concat(":/workflow");
    command = command.concat(" -i seqware/seqware_whitestar_pancancer");
    command = command.concat(" seqware bundle launch --no-metadata --engine whitestar-parallel");
    command = command.concat(" --dir /workflow");
    command = command.concat(" --ini /datastore/work.ini");
    // this may need to be more itelligent than just the exit code
    return execCommand(command);
  }
  
  public static void pushFileSetToHost(String[] sources, String destHost, String destPath, Map envs, Session session, File tmpIn) {
    for(String source : sources) {
      pushToHost(source, destHost, destPath, envs, session, tmpIn);
    }
  }
  
  public static int pushToHost(String source, String destHost, String destPath, Map envs, Session session, File tmpIn) {
    String localFile = source;
    int exitCode = -1;
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
      exitCode = execCommand(getCommand, envs);
    }
    try {
      Remote.fileTo(session, localFile, destPath.concat("/."));
    } catch(JSchException e) {
      throw new RuntimeException("Failure in SSH connection", e);
    }
    return exitCode;
  }
  
  public static void chmod(File f, String perms) {
    String command = "chmod ".concat(perms).concat(" ").concat(f.getAbsolutePath());
    execCommand(command);
  }
  
  private static int execCommand(String command) {
    Map<String,String> noEnv = new HashMap();
    return execCommand(command, noEnv);
  }
  
  
  private static int execCommand(String command, Map envs) {
    ProcessBuilder pb = new ProcessBuilder(command.split(" "));
    Map<String, String> pEnv = pb.environment();
    pEnv.putAll(envs);
    logger.info("Executing: " + command);
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

