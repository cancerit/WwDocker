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
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.plexus.util.cli.Arg;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.WriterStreamConsumer;

/**
 *
 * @author kr2
 */
public class Local {
  private static final Logger logger = LogManager.getLogger();
  
  private static String baseDockerCommand(BaseConfiguration config) {
    File workflow = Paths.get(config.getString("workflowDir"), config.getString("workflow").replaceAll(".*/", "").replaceAll("\\.zip$", "")).toFile();
    
    // probably want to clean the data store before we write the ini file
    //docker run --rm -h master -v /cgp/datastore:/datastore -v /cgp/workflows/Workflow_Bundle_SangerPancancerCgpCnIndelSnvStr_1.0.5.1_SeqWare_1.1.0-alpha.5:/workflow -i seqware/seqware_whitestar_pancancer rm -rf /datastore/
    
    String command = "docker run --rm -h master";
    command = command.concat(" -v ").concat(config.getString("datastoreDir")).concat(":/datastore");
    command = command.concat(" -v ").concat(workflow.getAbsolutePath()).concat(":/workflow");
    command = command.concat(" seqware/seqware_whitestar_pancancer");
    return command;
  }
  
  public static int runDocker(BaseConfiguration config, File iniFile) {
    /*
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
    
    String command = baseDockerCommand(config);
    command = command.concat(" seqware bundle launch --no-metadata --engine whitestar-parallel");
    command = command.concat(" --dir /workflow");
    command = command.concat(" --ini /datastore/").concat(iniFile.getName());
    // this may need to be more itelligent than just the exit code
    return execCommand(command, false);
  }
  
  public static int cleanDockerPath(BaseConfiguration config) {
    String command = baseDockerCommand(config);
    command = command.concat(" /bin/sh -c 'rm -rf /datastore/oozie-*'");
    return execCommand(command, true);
  }
  
  public static void pushFileSetToHost(List<File> sources, String destHost, String destPath, Map envs, Session session, File tmpIn) {
    for(File source : sources) {
      pushToHost(source.getAbsolutePath(), destHost, destPath, envs, session, tmpIn);
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
  
  public static int execCommand(String command, boolean shellCmd) {
    Map<String,String> noEnv = new HashMap();
    return execCommand(command, noEnv, shellCmd);
  }
  
  private static int execCommand(String command, Map envs, boolean shellCmd) {
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
    try {
      p = pb.start();
      exitCode = p.waitFor();
      if(exitCode != 0) {
        String progErr = IOUtils.toString(p.getErrorStream());
        Utils.logOutput(progErr, Level.ERROR);
      }
      Utils.logOutput(IOUtils.toString(p.getInputStream()), Level.TRACE);
    } catch(InterruptedException | IOException e) {
      logger.error(e.getMessage(), e);
    }
    finally {
      if(p != null) {
        p.destroy();
        exitCode = p.exitValue();
      }
    }
    return exitCode;
  }
}

