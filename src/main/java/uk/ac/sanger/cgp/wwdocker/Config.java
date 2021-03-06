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

package uk.ac.sanger.cgp.wwdocker;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.commons.configuration.PropertiesConfiguration;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.AccessControlException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author kr2
 */
public class Config {
  private static final Logger logger = LogManager.getLogger();
  
  private static final String[] optionalEnvs = {"http_proxy", "https_proxy", "no_proxy"};
  
  public static PropertiesConfiguration loadConfig(String configPath) {
    return loadConfig(configPath, ',', true);
  }
  
  public static PropertiesConfiguration loadConfig(String configPath, Character listDelimiter, boolean checkPermissions) {
    PropertiesConfiguration config;
    try {
      if(checkPermissions) {
        protectedFileCheck(configPath);
      }
      config = new PropertiesConfiguration(configPath);
      if(listDelimiter != null) {
        config.setListDelimiter(listDelimiter);
      }
    }
    catch(Exception e) {
      throw new RuntimeException("There was a problem reading your config file: " + configPath + "\n" + e.toString(), e);
    }
    return config;
  }
  
  public static void protectedFileCheck(String filename) throws AccessControlException, IOException {
    Path path = Paths.get(filename);
    Set<PosixFilePermission> permset = Files.getPosixFilePermissions(path);
    Iterator perm = permset.iterator();
    while(perm.hasNext()) {
      PosixFilePermission p = (PosixFilePermission) perm.next();
      if(p.equals(PosixFilePermission.GROUP_READ) || p.equals(PosixFilePermission.OTHERS_READ)) {
        throw new AccessControlException("Your configuration file (which contains passwords) is readable to others:\n"
                                          + "\tFile: " + filename.toString()
                                          + "\tPerm: " + PosixFilePermissions.toString(permset)
                                        );
      }
    }
  }
  
  public static BaseConfiguration loadWorkers(String workerConfig) {
    BaseConfiguration config;
    try {
      config = new PropertiesConfiguration(workerConfig);
    }
    catch (Exception e) {
      throw new RuntimeException(e.toString(), e);
    }
    return config;
  }
  
  public static Map<String, String> getEnvs(BaseConfiguration config) {
    Map<String,String> envs = new HashMap();
    for(String env : optionalEnvs) {
      String value = config.getString(env);
      if(value != null) {
        envs.put(env, value);
      }
    }
    return envs;
  }
}

