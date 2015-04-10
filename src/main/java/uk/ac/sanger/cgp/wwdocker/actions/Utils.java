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
package uk.ac.sanger.cgp.wwdocker.actions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author kr2
 */
public class Utils {
  private static final Logger logger = LogManager.getLogger();
  
  protected static String logOutput(String stdout) {
    return logOutput(stdout, Level.INFO);
  }
  
  protected static String logOutput(String stdout, Level type) {
    String remainder = null;
    String[] lines = stdout.split(System.lineSeparator());
    if(! stdout.endsWith(System.lineSeparator())) {
      remainder = lines[lines.length-1];
    }
    int limit = lines.length;
    if(remainder == null) {
      remainder = "";
    }
    else {
      limit--;
    }
    for(int i=0; i<limit; i++) {
      logger.log(type, "\t" + lines[i]);
    }
    return remainder;
  }
  
  public static File expandUserDirPath(BaseConfiguration config, String parameter, boolean checkExists) {
    File thing = expandUserFilePath(config, parameter, checkExists);
    if(!thing.isDirectory()) {
      throw new RuntimeException("Path indicated by '"+parameter+"="+config.getString(parameter)+"' is not a directory once expanded to '"+thing.getAbsolutePath()+"'");
    }
    return thing;
  }
  
  public static File expandUserFilePath(BaseConfiguration config, String parameter, boolean checkExists) {
    String localTmp = config.getString(parameter);
    if(localTmp.startsWith("~")) {
      logger.trace("Attempting to expand path: " + localTmp);
      if(localTmp.startsWith("~/")) {
        localTmp = localTmp.replaceFirst("^~", System.getProperty("user.home"));
      }
      else {
        throw new RuntimeException("Config value for '"+parameter+"' must be a full path or begin '~/'");
      }
      logger.trace("Expanded path: " + localTmp);
    }
    File expanded = new File(localTmp);
    if(!expanded.exists()) {
      throw new RuntimeException("File or path indicated by '"+parameter+"="+config.getString(parameter)+"' does not exist once expanded to '"+localTmp+"'");
    }
    return expanded;
  }
  
  public static File thisJarFile() {
    File thisJar = null;
    try {
      thisJar = new File(Remote.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath());
      
    }
    catch(URISyntaxException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
    return thisJar;
  }
  
  public static String fileDigest(File file) {
    String md5;
    try {
      FileInputStream fis = new FileInputStream(file);
      md5 = DigestUtils.md5Hex(fis);
      fis.close();
    } catch (FileNotFoundException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
    catch (IOException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
    return md5;
  }
  
  public static String objectToJson(Object obj) {
    ObjectMapper mapper = new ObjectMapper();
    String json;
    try {
      json = mapper.writeValueAsString(obj);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
    return json;
  }
  
  public static Object jsonToObject(String json, Class classType) {
    ObjectMapper mapper = new ObjectMapper();
    Object obj;
    try {
      obj = mapper.readValue(json, classType);
    }
    catch (IOException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
    return obj;
  }
}
