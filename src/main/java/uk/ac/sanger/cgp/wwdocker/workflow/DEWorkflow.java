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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.configuration.BaseConfiguration;
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
  
  private static final String[] logSearchCmd = {"find seqware-results/ -type f | grep -F '/logs/'"};
  
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
    command = command.concat(" -v ").concat(config.getString("datastoreDir")).concat(":/datastore");
    command = command.concat(" -v ").concat(config.getString("workflowDir")).concat(":/workflow");
    if(extras != null) {
      command = command.concat(extras);
    }
    command = command.concat(" seqware/seqware_whitestar_pancancer");
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
    extras = extras.concat(" -v /datastore/").concat(iniFile.getName()).concat(":/workflow.ini");
    // don't add the pem key here, just standardise in the ini file template
    
    String command = baseDockerCommand(config, extras);
    command = command.concat(" seqware bundle launch --no-metadata --engine whitestar-parallel");
    command = command.concat(" --dir /workflow/").concat(config.getString("workflow").replaceAll(".*/", "").replaceAll("\\.zip$", ""));
    // this may need to be more itelligent than just the exit code
    return execCommand(command, Config.getEnvs(config), false);
  }

  @Override
  public boolean provisionHost(String host, BaseConfiguration config, File thisJar, File tmpConf, String mode, Map<String, String> envs) throws InterruptedException {
    boolean provisioned = false;
    String[] makePaths = config.getStringArray("makePaths");
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
    
    Session ssh = Remote.getSession(config, host);
    
    Remote.createPaths(ssh, makePaths);
    Remote.chmodPaths(ssh, "a+wrx", makePaths, true);
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
     //|| Local.pushFileSetToHost(Utils.getGnosKeys(config), host, gnosDest.toString(), envs, ssh, localTmp) != 0 // GNOS keys
      || Local.pushFileSetToHost(Utils.getGnosKeys(config), host, remoteWorkflowDir, envs, ssh, localTmp) != 0 // GNOS keys
     || Remote.startWorkerDaemon(ssh, thisJar.getName(), tmpConf.getName(), mode) != 0) {
      return provisioned;
    }
    provisioned = true;
    return provisioned;
  }
  
}
