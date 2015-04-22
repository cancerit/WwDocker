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

package uk.ac.sanger.cgp.wwdocker.interfaces;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.configuration.BaseConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.ac.sanger.cgp.wwdocker.enums.HostStatus;

/**
 *
 * @author kr2
 */
public interface Workflow {
  List filesToPush(File iniFile);
  List filesToPull(File iniFile);
  
  default String iniPathByState(BaseConfiguration config, String iniFile, HostStatus hs) {
    File tmp = new File(iniFile);
    String statePath = config.getString("wfl_inis");
    if(!statePath.endsWith("/")) {
      statePath = statePath.concat("/");
    }
    return statePath.concat(hs.name()).concat("/").concat(tmp.getName());
  }
  
  default void iniUpdate(File iniFrom, BaseConfiguration config, HostStatus hs) {
    List<File> iniFiles = new ArrayList(1);
    iniFiles.add(iniFrom);
    iniUpdate(iniFiles, config, hs);
  }
  
  default void iniUpdate(List<File> inisFrom, BaseConfiguration config, HostStatus hs) {
    String iniBaseTo = config.getString("wfl_inis");
    if(!iniBaseTo.endsWith("/")) {
      iniBaseTo = iniBaseTo.concat("/");
    }
    iniBaseTo = iniBaseTo.concat(hs.name()).concat("/");
    File dirCreate = new File(iniBaseTo);
    if(!dirCreate.exists()) {
      if(!dirCreate.mkdirs()){
        throw new RuntimeException("Failed to create full path: " + dirCreate.getAbsolutePath());
      }
    }
    for(File iniFrom : inisFrom) {
      File iniTo = new File(iniBaseTo.concat(iniFrom.getName()));
      try {
        Files.move(iniFrom.toPath(), iniTo.toPath(), StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
        throw new RuntimeException(e.getMessage(), e);
      }
    }
  }
}
