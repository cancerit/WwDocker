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

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Properties;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author kr2
 */
public class Remote {
  private static final Logger logger = LogManager.getLogger();
  private static final int SSH_TIMEOUT = 20000; // 20 seconds
  
  
  private static Session addNewHost(BaseConfiguration config, String host) {
    Session session = null;
    logger.warn("Host '" + host + "' is not known attempting to resolve");
    try {
      Session thisSess = unconnectedSession(config, host);

      Properties props = new Properties();
      props.put("StrictHostKeyChecking", "no");  
      thisSess.setConfig(props);

      thisSess.connect(SSH_TIMEOUT);
      
      session = thisSess;
    }
    catch(JSchException e) {
      throw new RuntimeException("Failure in SSH connection: "+e.getMessage(), e);
    }
    logger.warn("Successfully added new key for '" + host + "'");
    return session;
  }
  
  private static File privateKeyFile() {
    String fSep = System.getProperty("file.separator");
    File key = new File(System.getProperty("user.home") + fSep + ".ssh" + fSep + "id_dsa");
    if(!key.exists()) {
      key = new File(System.getProperty("user.home") + fSep + ".ssh" + fSep + "id_rsa");
    }
    if(!key.exists()) {
      throw new RuntimeException("Unable to find any identity key files, e.g. ~/.ssh/id_dsa or ~/.ssh/id_rsa");
    }
    return key;
  }
  
  private static Session unconnectedSession(BaseConfiguration config, String host) {
    Session session;
    String fSep = System.getProperty("file.separator");
    String userKnownHosts = System.getProperty("user.home") + fSep + ".ssh" + fSep + "known_hosts";
    try {
      JSch jsch = new JSch();
      jsch.setKnownHosts(userKnownHosts);
      jsch.addIdentity(privateKeyFile().getAbsolutePath());
      Session thisSess=jsch.getSession(config.getString("ssh_user"), host, 22);
      thisSess.setServerAliveInterval(1000);
      thisSess.setPassword(config.getString("ssh_pw"));
      session = thisSess;
    }
    catch(JSchException e) {
      throw new RuntimeException("Failure in SSH connection: "+e.getMessage(), e);
    }
    return session;
  }
  
  public static Session getSession(BaseConfiguration config, String host) {
    Session session;
    try {
      Session thisSess = unconnectedSession(config, host);
      thisSess.connect(SSH_TIMEOUT);
      logger.info("Host '" + host + "' is known");
      session = thisSess;
    }
    catch(JSchException e) {
      if(e.getMessage().startsWith("UnknownHostKey")) {
        session = addNewHost(config, host);
      }
      else {
        // still falls over if the host key is changed
        throw new RuntimeException("Failure in SSH connection: "+e.getMessage(), e);
      }
    }
    return session;
  }
  
  public static boolean dockerRunning(Session session, String user) {
    boolean isRunning = true; // assume least destructive state
    String command = "ps -fu " + user + " | grep docker | grep -cv grep";
    int exitCode = 0;
    try {
      exitCode = execCommand(session, command);
    } catch(JSchException e) {
      if(e.getMessage().contains("session is down")) {
        logger.warn("Session terminated mid query, abort check");
      }
      else {
        throw new RuntimeException("Failure in SSH connection", e);
      }
    }
    if(exitCode == 1) {
      isRunning = false;
    }
    return isRunning;
  }
  
  public static boolean workerRunning(Session session, String user) {
    boolean isRunning = true; // assume least destructive state
    String command = "ps -fu " + user + " | grep -E \"WwDocker-.*.jar\" | grep -cv grep";
    int exitCode = 0;
    try {
      exitCode = execCommand(session, command);
    } catch(JSchException e) {
      if(e.getMessage().contains("session is down")) {
        logger.warn("Session terminated mid query, abort check");
      }
      else {
        throw new RuntimeException("Failure in SSH connection", e);
      }
    }
    if(exitCode == 1) {
      isRunning = false;
    }
    return isRunning;
  }
  
  public static int dockerLoad(Session session, String[] images, String workspace) {
    int exitCode = -1;
    if(images.length == 1 && images[0].length() == 0) {
      return 0;
    }
    try {
      for(String i : images) {
        String destFile;
        if(i.startsWith("/")) {
          destFile = i;
        }
        else {
          destFile = curl(session, i, workspace);
          if(destFile == null) {
            logger.error("Failed to retrieve file via curl: "+ i);
            return 1;
          }
        }
        String command = "docker load -i " + destFile;
        exitCode = execCommand(session, command);
        if(exitCode != 0) {
          break;
        }
      }
    } catch(JSchException e) {
      throw new RuntimeException("Failure in SSH connection", e);
    }
    return exitCode;
  }
  
  public static int dockerPull(Session session, String[] images) {
    int exitCode = -1;
    try {
      for(String i : images) {
        String command = "docker pull " + i;
        exitCode = execCommand(session, command);
        if(exitCode != 0) {
          break;
        }
      }
    } catch(JSchException e) {
      throw new RuntimeException("Failure in SSH connection", e);
    }
    return exitCode;
  }
  
  public static void cleanupOldImages(Session session) {
    String command = "docker images | grep \"<none>\" | awk '{print $3}' | xargs docker rmi";
    try {
      execCommand(session, command);
    } catch(JSchException e) {
      throw new RuntimeException("Failure in SSH connection", e);
    }
  }
  
  public static void cleanHost(Session session, String[] paths) {
    if(paths.length == 0) {
      throw new RuntimeException("Potentially deleting root of storage, aborting");
    }
    for(String p : paths) {
      String command = "rm -rf";
      if(p.length() == 0) {
        throw new RuntimeException("Potentially deleting root of storage, aborting");
      }
      command = command.concat(" ").concat(p).concat("/*");
      paramExec(session, command);
    }
  }
  
  public static int cleanFiles(Session session, String[] files) {
    String command = "rm -f";
    for(String p : files) {
      if(p.equals("/*")) {
        throw new RuntimeException("Potentially deleting root of storage, aborting");
      }
      command = command.concat(" ").concat(p);
    }
    return paramExec(session, command);
  }
  
  public static int expandJre(Session session, File localJre) {
    String command = "tar --strip-components=1 -C /opt/wwdocker/jre -zxf /opt/wwdocker/".concat(localJre.getName());
    return paramExec(session, command);
  }
  
  public static int expandWorkflow(Session session, File localWorkflow, File seqwareJar, String workflowBase) {
    //java -cp seqware-distribution-1.1.0-alpha.6-full.jar net.sourceforge.seqware.pipeline.tools.UnZip --input-zip
    String workflowDir = workflowBase.concat("/").concat(localWorkflow.getName());
    workflowDir = workflowDir.replaceFirst("[.]zip$", "");
    String command = "/opt/wwdocker/jre/bin/java -Xmx128m -cp ";
    command = command.concat(seqwareJar.getAbsolutePath());
    command = command.concat(" net.sourceforge.seqware.pipeline.tools.UnZip --input-zip ");
    command = command.concat(localWorkflow.getAbsolutePath());
    command = command.concat(" --output-dir ");
    command = command.concat(workflowDir);
    return paramExec(session, command);
  }
  
  public static int createPaths(Session session, List<String> paths) {
    String[] array = paths.toArray(new String[paths.size()]);
    return createPaths(session, array);
  }
  
  public static int createPaths(Session session, String[] paths) {
    String command = "mkdir -p";
    return paramExec(session, command, paths);
  }
  
  public static int chmodPath(Session session, String mode, String path, boolean recursive) {
    String[] paths = {path};
    return chmodPaths(session, mode, paths, recursive);
  }
  
  public static int chmodPaths(Session session, String mode, List<String> paths, boolean recursive) {
    String[] array = paths.toArray(new String[paths.size()]);
    return chmodPaths(session, mode, array, recursive);
  }
  
  public static int chmodPaths(Session session, String mode, String[] paths, boolean recursive) {
    String command = "chmod ";
    if(recursive) {
      command = command.concat("-R ");
    }
    command = command.concat(mode);
    return paramExec(session, command, paths);
  }
  
  private static int paramExec(Session session, String command) {
    return paramExec(session, command, new String[0]);
  }
  
  private static int paramExec(Session session, String command, String[] params) {
    int exitCode = 0;
    for(String param:params) {
      command = command.concat(" ").concat(param);
    }
    try {
      exitCode = execCommand(session, command);
    } catch(JSchException e) {
      throw new RuntimeException("Failure in SSH connection", e);
    }
    return exitCode;
  }
  
  public static void listDir(Session session, String path) {
    String command = "ls -l "+ path;
    try {
      execCommand(session, command);
    } catch(JSchException e) {
      throw new RuntimeException("Failure in SSH connection", e);
    }
  }
  
  public static int startWorkerDaemon(Session session, String jarName, String confName, String mode) {
    int exitCode = -1;
    //java -Dlog4j.configurationFile="config/log4j.properties.xml" -jar target/WwDocker-0.1.jar Primary config/default.cfg
    String command = "/opt/wwdocker/jre/bin/java -Xmx256m -Dlog4j.configurationFile=\"/opt/wwdocker/log4j.properties_worker.xml\" -jar /opt/wwdocker/"
                      .concat(jarName)
                      .concat(" /opt/wwdocker/").concat(confName).concat(" Worker");
    if(mode != null) {
      command = command.concat(" ").concat(mode);
    }
    command = command.concat(" >& /dev/null &");
    try {
      exitCode = execCommand(session, command);
    } catch(JSchException e) {
      throw new RuntimeException("Failure in SSH connection while starting daemon", e);
    }
    return exitCode;
  }
  
  public static String curl(Session session, String source, String destPath) {
    String finalPath = null;
    try {
      String[] elements = source.split("/");
      if(!destPath.endsWith(System.getProperty("file.separator"))) {
        destPath = destPath.concat(System.getProperty("file.separator"));
      }
      destPath = destPath.concat(elements[elements.length-1]);
      // -z only transfer if modified
      String getCommand = "curl -RLsS"
                            .concat(" -z ").concat(destPath)
                            .concat(" -o ").concat(destPath)
                            .concat(" ").concat(source);
      if(execCommand(session, getCommand) == 0){
        finalPath = destPath;
      }
    }
    catch(JSchException e) {
      throw new RuntimeException("Failure in SSH connection", e);
    }
    return finalPath;
  }
  
  private static int execCommand(Session session, String command) throws JSchException {
    int exitCode = -1;
    
    Channel channel=session.openChannel("exec");
    ((ChannelExec)channel).setCommand(command);
    logger.trace("Remote exection of command: [".concat(session.getHost()).concat("]: ").concat(command));
    String fullOut = new String();
    try {
      InputStream in=channel.getInputStream();
      channel.connect();
      byte[] tmp=new byte[1024];
      while(true){
        while(in.available()>0){
          int i=in.read(tmp, 0, 1024);
          if(i<0)break;
          fullOut = fullOut.concat(new String(tmp, 0, i));
          fullOut = Utils.logOutput(fullOut);
        }
        if(channel.isClosed()){
          if(in.available()>0) continue; 
          exitCode = channel.getExitStatus();
          Utils.logOutput(fullOut+System.lineSeparator());
          logger.info("Exit code: " + exitCode);
          break;
        }
        try{Thread.sleep(1000);}catch(Exception ee){}
      }
    }
    catch(IOException e) {
      throw new JSchException("IOException during ssh action: "+ command, e);
    }
    channel.disconnect();
    return exitCode;
  }
  
  private static boolean remoteIsEqual(Session session, String localPath, String remotePath) throws JSchException {
    boolean isEqual = false;
    
    File lFile = new File(localPath);
    
    if(remotePath.endsWith("/.")) {
      remotePath = remotePath.substring(0, remotePath.length()-1).concat(lFile.getName());
    }

    String rawString = remoteExecStdout(session, "date --utc --reference=" + remotePath + " +%s");
    if(rawString == null) {
      logger.debug("no existing remote file: "+ remotePath);
    }
    else {
      long rMtime = Long.valueOf(rawString).longValue() * 1000;
      long rSize = Long.valueOf(remoteExecStdout(session, "ls -l --full-time " + remotePath).split("\\s+")[4]).longValue();

      logger.debug("lFile size: " + lFile.length());
      logger.debug("rFile size: " + rSize);
      logger.debug("lFile mtime: " + lFile.lastModified());
      logger.debug("rFile mtime: " + rMtime);

      if(rMtime == lFile.lastModified() && rSize == lFile.length()) {
        isEqual = true;
      }
    }
    
    return isEqual;
  }
  
  public static boolean processExists(Session session, Long pid) throws JSchException {
    boolean exists = false;
    int exitCode = execCommand(session, "ps " + pid);
    if(exitCode == 0) {
      exists = true;
    }
    return exists;
  }
  
  private static String remoteExecStdout(Session session, String command) throws JSchException {
    int exitCode = -1;
    
    Channel channel=session.openChannel("exec");
    ((ChannelExec)channel).setCommand(command);
    logger.trace("Remote exection of command: [".concat(session.getHost()).concat("]: ").concat(command));
    String fullOut = new String();
    try {
      InputStream in=channel.getInputStream();
      channel.connect();
      byte[] tmp=new byte[1024];
      while(true){
        while(in.available()>0){
          int i=in.read(tmp, 0, 1024);
          if(i<0)break;
          fullOut = fullOut.concat(new String(tmp, 0, i));
        }
        if(channel.isClosed()){
          if(in.available()>0) continue; 
          exitCode = channel.getExitStatus();
          logger.info("Exit code: " + exitCode);
          break;
        }
        try{Thread.sleep(1000);}catch(Exception ee){}
      }
    }
    catch(IOException e) {
      throw new JSchException("IOException during ssh action: "+ command, e);
    }
    channel.disconnect();
    if(exitCode != 0) {
      fullOut = null;
    }
    else {
      fullOut = fullOut.trim();
    }
    return fullOut;
  }
  
  public static int fileTo(Session session, String localFile, String remoteFile) throws JSchException {
    if(remoteIsEqual(session, localFile, remoteFile)) {
      logger.info("Files are equal, not performing SCP");
      return 0;
    }
    
    logger.info("Sending file to remote [".concat(session.getHost()).concat("]: ").concat(localFile));
    boolean ptimestamp = true;
    // exec 'scp -t rfile' remotely
    String command="scp " + (ptimestamp ? "-p" :"") +" -t "+remoteFile;
    logger.trace(command);
    
    Channel channel=session.openChannel("exec");
    ((ChannelExec)channel).setCommand(command);

    try {
      // get I/O streams for remote scp
      OutputStream out = channel.getOutputStream();
      InputStream in = channel.getInputStream();

      channel.connect();

      FileInputStream fis=null;

      if (checkAck(in) != 0) {
        logger.error("scp failed");
        return 1;
      }

      File _lfile = new File(localFile);

      if (ptimestamp) {
        command = "T " + (_lfile.lastModified() / 1000) + " 0";
        // The access time should be sent here,
        // but it is not accessible with JavaAPI ;-<
        command += (" " + (_lfile.lastModified() / 1000) + " 0\n");
        out.write(command.getBytes());
        out.flush();
        if (checkAck(in) != 0) {
          logger.error("scp failed");
          return 1;
        }
      }

      // send "C0644 filesize filename", where filename should not include '/'
      long filesize = _lfile.length();
      command = "C0644 " + filesize + " ";
      if (localFile.lastIndexOf('/') > 0) {
        command += localFile.substring(localFile.lastIndexOf('/') + 1);
      } else {
        command += localFile;
      }
      command += "\n";
      out.write(command.getBytes());
      out.flush();
      if (checkAck(in) != 0) {
        logger.error("scp failed");
        return 1;
      }

      // send a content of lfile
      fis = new FileInputStream(localFile);
      byte[] buf = new byte[1024];
      while (true) {
        int len = fis.read(buf, 0, buf.length);
        if (len <= 0) {
          break;
        }
        out.write(buf, 0, len); //out.flush();
      }
      fis.close();
      fis = null;
      // send '\0'
      buf[0] = 0;
      out.write(buf, 0, 1);
      out.flush();
      if (checkAck(in) != 0) {
        logger.error("scp failed");
        return 1;
      }
      out.close();
    }
    catch(IOException e) {
      throw new JSchException("IOException during ssh action: "+ command, e);
    }
    channel.disconnect();
    logger.info("Send complete");
    return 0;
  }
  
  private static int checkAck(InputStream in) throws IOException {
    int b = in.read();
    // b may be 0 for success,
    //          1 for error,
    //          2 for fatal error,
    //          -1
    if (b == 0) {
      return b;
    }
    if (b == -1) {
      return b;
    }

    if (b == 1 || b == 2) {
      StringBuffer sb = new StringBuffer();
      int c;
      do {
        c = in.read();
        sb.append((char) c);
      } while (c != '\n');
      if (b == 1) { // error
        logger.error(sb.toString());
      }
      if (b == 2) { // fatal error
        logger.fatal(sb.toString());
      }
    }
    return b;
  }

  public static void closeSsh(Session ssh) {
    ssh.disconnect();
    try {
      while (ssh.isConnected()) {
        Thread.sleep(10);
      }
    } catch (InterruptedException e) {
      logger.warn("Issue during closing ssh session... continuing", e);
    }
    return;
  }
}

