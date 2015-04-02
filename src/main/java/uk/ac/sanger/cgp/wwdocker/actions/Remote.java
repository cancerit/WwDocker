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
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author kr2
 */
public class Remote {
  private static final Logger logger = LogManager.getLogger();
  private static final int SSH_TIMEOUT = 10000; // 10 seconds
  
  public static Session getSession(BaseConfiguration config, String host) {
    Session session;
    try {
      String fSep = System.getProperty("file.separator");
      String userKnownHosts = System.getProperty("user.home") + fSep + ".ssh" + fSep + "known_hosts";
      JSch jsch = new JSch();
      jsch.setKnownHosts(userKnownHosts);
      session=jsch.getSession(config.getString("ssh_user"), host, 22);
      session.setPassword(config.getString("ssh_pw"));
      session.connect(SSH_TIMEOUT);
    }
    catch(JSchException e) {
      throw new RuntimeException("Failure in SSH connection", e);
    }
    return session;
  }
  
  public static void stageDocker(Session session, String image) {
    String command = "docker pull " + image;
    try {
      execCommand(session, command);
    } catch(JSchException e) {
      throw new RuntimeException("Failure in SSH connection", e);
    }
  }
  
  public static void createPaths(Session session, String[] paths) {
    String command = "mkdir -p";
    paramExec(session, command, paths);
  }
  
  public static void chmodPaths(Session session, String mode, String[] paths) {
    String command = "chown " + mode;
    paramExec(session, command, paths);
  }
  
  private static void paramExec(Session session, String baseCommand, String[] params) {
    String command = null;
    for(String param:params) {
      command = baseCommand.concat(" ").concat(param);
    }
    try {
      execCommand(session, command);
    } catch(JSchException e) {
      throw new RuntimeException("Failure in SSH connection", e);
    }
  }
  
  public static void listDir(Session session, String path) {
    String command = "ls -l "+ path;
    try {
      execCommand(session, command);
    } catch(JSchException e) {
      throw new RuntimeException("Failure in SSH connection", e);
    }
  }
  
  private static int execCommand(Session session, String command) throws JSchException {
    int exitCode = -1;
    
    Channel channel=session.openChannel("exec");
    ((ChannelExec)channel).setCommand(command);
    logger.trace("Remote exection of command: "+ command);
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
          fullOut = Utils.logOutput(fullOut+System.lineSeparator());
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
}

