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

package uk.ac.sanger.cgp.wwdocker.workflow;

import com.jcraft.jsch.Session;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.ac.sanger.cgp.wwdocker.Config;
import uk.ac.sanger.cgp.wwdocker.actions.Local;
import static uk.ac.sanger.cgp.wwdocker.actions.Local.execCommand;
import uk.ac.sanger.cgp.wwdocker.actions.Remote;
import uk.ac.sanger.cgp.wwdocker.actions.Utils;
import uk.ac.sanger.cgp.wwdocker.interfaces.Workflow;

/**
 *
 * @author kr2
 */
public class DEWorkflow implements Workflow {
  private static final Logger logger = LogManager.getLogger();
  BaseConfiguration config;
  
  private static final String[] logSearchCmd = {"find oozie-*/ -type f | grep 'gtdownload.*log$'",
                                                "find oozie-*/shared_workspace/oozie-*/generated-scripts -type f",
                                                "find oozie-*/shared_workspace/oozie-*/delly_results -type f | grep '.log$'",};
  
  public DEWorkflow(BaseConfiguration config) {
    this.config = config;
  }
  
  @Override
  public String[] getFindLogsCmds() {
    return logSearchCmd;
  }
  
  @Override
  public List filesToPush(File iniFile) {
    List files = new ArrayList();
    // see example in SangerWorkflow if we need this later
    return files;
  }
  
  @Override
  public  List filesToPull(File iniFile) {
    List files = new ArrayList();
    //@TODO
    return files;
  }
  
    @Override
  public String baseDockerCommand(BaseConfiguration config, String extras) {
    //File workflow = Paths.get(config.getString("workflowDir"), config.getString("workflow").replaceAll(".*/", "").replaceAll("\\.zip$", "")).toFile();
    
    // probably want to clean the data store before we write the ini file
    //docker run --rm -h master -v /cgp/datastore:/datastore -v /cgp/workflows/Workflow_Bundle_SangerPancancerCgpCnIndelSnvStr_1.0.5.1_SeqWare_1.1.0-alpha.5:/workflow -i seqware/seqware_whitestar_pancancer rm -rf /datastore/
    
    String command = "docker run --rm -h master";
    // this is an oddity of the DKFZ version to ensure that paths are handled correctly in the inner docker call
    command = command.concat(" -v ").concat(config.getString("datastoreDir")).concat(":").concat(config.getString("datastoreDir"));
    command = command.concat(" -v ").concat(config.getString("workflowDir")).concat(":/workflow");
    if(extras != null) {
      command = command.concat(extras);
    }
    command = command.concat(" ").concat(seqwareWhiteStarImage(config));
    return command;
  }
  
  @Override
  public int runDocker(BaseConfiguration config, File iniFile) {
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
    
    String extras = " -v /var/run/docker.sock:/var/run/docker.sock";
    extras = extras.concat(" -v ").concat(config.getString("datastoreDir")).concat("/").concat(iniFile.getName()).concat(":/workflow.ini");
    // don't add the pem key here, just standardise in the ini file template
    
    String command = baseDockerCommand(config, extras);
    command = command.concat(" bash -c \"sed -i 's|/datastore|" + config.getString("datastoreDir") + "|g' /home/seqware/.seqware/settings ;");
    command = command.concat(" sed -i 's|OOZIE_RETRY_MAX=.*|OOZIE_RETRY_MAX=0|' /home/seqware/.seqware/settings ;");
    command = command.concat(" seqware bundle launch --no-metadata --engine whitestar");
    command = command.concat(" --dir /workflow/").concat(config.getString("workflow").replaceAll(".*/", "").replaceAll("\\.zip$", ""));
    command = command.concat(" --ini /workflow.ini");
    command = command.concat("\""); // close quotes on bash command
    
    // this may need to be more itelligent than just the exit code
    return execCommand(command, Config.getEnvs(config), true);
  }

  @Override
  public boolean provisionHost(String host, BaseConfiguration config, File thisJar, File tmpConf, String mode, Map<String, String> envs) throws InterruptedException {
    boolean provisioned = false;
    String remoteWorkflowDir = config.getString("workflowDir");
    String localSeqwareJar = config.getString("seqware");
    String localWorkflowZip = config.getString("workflow");
    File jreDist = Utils.expandUserFile(config, "jreDist", true);
    File remoteSeqwareJar = new File(remoteWorkflowDir.concat("/").concat(localSeqwareJar.replaceAll(".*/", "")));
    File remoteWorkflowZip = new File(remoteWorkflowDir.concat("/").concat(localWorkflowZip.replaceAll(".*/", "")));
    String[] pullDockerImages = config.getStringArray("pullDockerImages");
    String[] curlDockerImages = config.getStringArray("curlDockerImages");
    String optDir = "/opt";
    String workerLog = config.getString("log4-worker");
    File localTmp = Utils.expandUserDirPath(config, "primaryLargeTmp", true);
    
    List<String> createPaths = new ArrayList();
    createPaths.add("/opt");
    createPaths.add(remoteWorkflowDir);
    createPaths.add(config.getString("datastoreDir"));
    
    Session ssh = Remote.getSession(config, host);
    
    Remote.createPaths(ssh, createPaths);
    Remote.chmodPaths(ssh, "a+wrx", createPaths, true);
    Remote.cleanFiles(ssh, new String[]{config.getString("log4-delete")});
    
    Remote.cleanupOldImages(ssh); // incase lots of stale ones are already present
    if (Remote.dockerPull(ssh, pullDockerImages) != 0 || Remote.dockerLoad(ssh, curlDockerImages, remoteWorkflowDir) != 0) {
      return provisioned;
    }
    Remote.cleanupOldImages(ssh); // incase lots of stale ones are already present
    
    if(Remote.curl(ssh, localSeqwareJar, remoteWorkflowDir) == null) {
      return provisioned;
    }
    
    Local.pushToHost(jreDist.getAbsolutePath(), host, optDir, envs, ssh, localTmp);
    if (Remote.expandJre(ssh, jreDist) != 0) {
      return provisioned;
    }
    Remote.curl(ssh, localWorkflowZip, remoteWorkflowDir);
    if (Remote.expandWorkflow(ssh, remoteWorkflowZip, remoteSeqwareJar, remoteWorkflowDir) != 0) {
      return provisioned;
    }
    String workflowBase = remoteWorkflowZip.getName().replaceAll("\\.zip$", "");
    //Path gnosDest = Paths.get(remoteWorkflowDir, workflowBase);
    if (Local.pushToHost(thisJar.getAbsolutePath(), host, optDir, envs, ssh, localTmp) != 0 // this jar file
     || Local.pushToHost(workerLog, host, optDir, envs, ssh, localTmp) != 0 // worker log config
     || Local.pushToHost(tmpConf.getAbsolutePath(), host, optDir, envs, ssh, localTmp) != 0 // config file
     || Remote.chmodPath(ssh, "go-wrx", optDir.concat("/*"), true) != 0 // file will have passwords
     || Local.pushFileSetToHost(Utils.getGnosKeys(config), host, config.getString("datastoreDir"), envs, ssh, localTmp) != 0 // GNOS keys, note DATASTORE
     || Remote.chmodPath(ssh, "a+r", config.getString("datastoreDir").concat("/*.pem"), false) != 0 // need to ensure these are readable within the image
     || Remote.startWorkerDaemon(ssh, thisJar.getName(), tmpConf.getName(), mode) != 0) {
      return provisioned;
    }
    provisioned = true;
    return provisioned;
  }
  
  @Override
  public int cleanDockerPath(BaseConfiguration config) {
    String command = baseDockerCommand(config, null);
    String datastore = config.getString("datastoreDir");
    List<String> args = new ArrayList(Arrays.asList(command.split(" ")));
    args.add("/bin/bash");
    args.add("-c");
    args.add("rm -rf "
      +datastore+ "/oozie-* "
      +datastore+ "/*.ini "
      +datastore+ "/logs.tar.gz "
      +datastore+ "/toInclude.lst"
      +datastore+ "/DEWorkflowData/dkfz/gtdownload-*.log"
    );
    
    ProcessBuilder pb = new ProcessBuilder(args);

    Map<String, String> pEnv = pb.environment();
    pEnv.putAll(Config.getEnvs(config));
    logger.info("Executing: " + String.join(" ", args));
    int exitCode = -1;
    Process p = null;
    try {
      p = pb.start();
      String progErr = IOUtils.toString(p.getErrorStream());
      String progOut = IOUtils.toString(p.getInputStream());
      exitCode = p.waitFor();
      Utils.logOutput(progErr, Level.ERROR);
      Utils.logOutput(progOut, Level.TRACE);
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
