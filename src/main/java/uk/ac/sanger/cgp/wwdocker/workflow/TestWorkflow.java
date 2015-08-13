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
public class TestWorkflow implements Workflow {
  private static final Logger logger = LogManager.getLogger();
  BaseConfiguration config;
  
  private static final String[] logSearchCmd = {};
  
  public TestWorkflow(BaseConfiguration config) {
    this.config = config;
  }
  
  @Override
  public String[] getFindLogsCmds() {
    return logSearchCmd;
  }
  
  @Override
  public List filesToPush(File iniFile) {
    List files = new ArrayList();
    files.add(iniFile);
    logger.trace(iniFile);
    BaseConfiguration wkflConf = Config.loadConfig(iniFile.getAbsolutePath(), new Character(':'), false);
    if(wkflConf.getBoolean("testMode")) {
      logger.info(iniFile.getAbsolutePath() + " is set as testMode=true, no BAMs to transfer");
    }
    else {
      // Add construct the paths to the local files
      List analysisIds = wkflConf.getList("tumourAnalysisIds");
      analysisIds.add(wkflConf.getList("controlAnalysisId"));
      List bamFiles = wkflConf.getList("tumourBams");
      bamFiles.add(wkflConf.getList("controlBam"));
      if(analysisIds.size() != bamFiles.size()) {
        throw new RuntimeException("Number of *AnalysisId[s] is not equal to the number of *Bam[s] in workfile: " + iniFile.getAbsolutePath());
      }
      for(int i=0; i<analysisIds.size(); i++) {
        String analysisId = (String) analysisIds.get(i);
        String bamFile = (String) bamFiles.get(i);
        File bamPath = new File(config.getString("data_root")
                                .concat("/").concat(analysisId)
                                .concat("/").concat(bamFile)
                              );
        if(!bamPath.exists()) {
          throw new RuntimeException("Unable to find BAM file (" + bamPath.getAbsolutePath() + ") expected from worfile: " + iniFile.getAbsolutePath());
        }
        files.add(bamPath);
      }
    }
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
    File workflow = Paths.get(config.getString("workflowDir"), config.getString("workflow").replaceAll(".*/", "").replaceAll("\\.zip$", "")).toFile();
    
    // probably want to clean the data store before we write the ini file
    //docker run --rm -h master -v /cgp/datastore:/datastore -v /cgp/workflows/Workflow_Bundle_SangerPancancerCgpCnIndelSnvStr_1.0.5.1_SeqWare_1.1.0-alpha.5:/workflow -i seqware/seqware_whitestar_pancancer rm -rf /datastore/
    
    String command = "docker run --rm -h master";
    command = command.concat(" -v ").concat(config.getString("datastoreDir")).concat(":/datastore");
//    command = command.concat(" -v ").concat(workflow.getAbsolutePath()).concat(":/workflow");
    command = command.concat(" ").concat(seqwareWhiteStarImage(config));
    return command;
  }
  
  @Override
  public int runDocker(BaseConfiguration config, File iniFile) {
   
    String command = baseDockerCommand(config, null);
    command = command.concat(" perl /datastore/run.pl ")
      .concat(iniFile.getName().replace(".ini", "")); // this changes the amount of output
    return execCommand(command, Config.getEnvs(config), false);
  }

  @Override
  public boolean provisionHost(String host, BaseConfiguration config, File thisJar, File tmpConf, String mode, Map<String, String> envs) throws InterruptedException {
    boolean provisioned = false;
    String remoteWorkflowDir = config.getString("workflowDir");
    String localSeqwareJar = config.getString("seqware");
    File jreDist = Utils.expandUserFile(config, "jreDist", true);
    String[] pullDockerImages = config.getStringArray("pullDockerImages");
    String optDir = "/opt/wwdocker";
    String workerLog = config.getString("log4-worker");
    File localTmp = Utils.expandUserDirPath(config, "primaryLargeTmp", true);
    
    List<String> createPaths = new ArrayList();
    createPaths.add("/opt/wwdocker");
    createPaths.add("/opt/wwdocker/jre");
    createPaths.add(remoteWorkflowDir);
    createPaths.add(config.getString("datastoreDir"));
    
    Session ssh = Remote.getSession(config, host);
    
    Remote.createPaths(ssh, createPaths);
    Remote.chmodPaths(ssh, "a+wrx", createPaths, true);
    Remote.cleanFiles(ssh, new String[]{config.getString("log4-delete")});
    
    if (Remote.dockerPull(ssh, pullDockerImages) != 0) {
      return provisioned;
    }
    
    if(Remote.curl(ssh, localSeqwareJar, remoteWorkflowDir) == null) {
      return provisioned;
    }
    
    Local.pushToHost(jreDist.getAbsolutePath(), host, optDir, envs, ssh, localTmp);
    if (Remote.expandJre(ssh, jreDist) != 0) {
      return provisioned;
    }
    
    Local.pushToHost("testData/run.pl", host, config.getString("datastoreDir"), envs, ssh, localTmp);
    
    if (Local.pushToHost(thisJar.getAbsolutePath(), host, optDir, envs, ssh, localTmp) != 0 // this jar file
     || Local.pushToHost(workerLog, host, optDir, envs, ssh, localTmp) != 0 // worker log config
     || Local.pushToHost(tmpConf.getAbsolutePath(), host, optDir, envs, ssh, localTmp) != 0 // config file
     || Remote.chmodPath(ssh, "go-wrx", optDir.concat("/*"), true) != 0 // file will have passwords
     || Remote.startWorkerDaemon(ssh, thisJar.getName(), tmpConf.getName(), mode) != 0) {
      return provisioned;
    }
    provisioned = true;
    return provisioned;
  }
  
}
