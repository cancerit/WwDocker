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
package uk.ac.sanger.cgp.wwdocker.beans;

import java.io.File;
import java.io.IOException;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author kr2
 */
public class WorkflowIni {
  private File iniFile;
  private String iniContent;
  
  public WorkflowIni() {
    // required for jsonToObject
  }
  
  public WorkflowIni(File iniFile) throws IOException {
    this.iniFile = iniFile;
    iniContent = FileUtils.readFileToString(iniFile, null);
  }

  /**
   * @return the iniFile
   */
  public File getIniFile() {
    return iniFile;
  }

  /**
   * @param iniFile the iniFile to set
   */
  public void setIniFile(File iniFile) {
    this.iniFile = iniFile;
  }

  /**
   * @return the iniContent
   */
  public String getIniContent() {
    return iniContent;
  }

  /**
   * @param iniContent the iniContent to set
   */
  public void setIniContent(String iniContent) {
    this.iniContent = iniContent;
  }
  
  @Override
  public String toString() {
    StringBuilder result = new StringBuilder();
    String NEW_LINE = System.getProperty("line.separator");
    result.append(this.getClass().getName()).append(" Object {").append(NEW_LINE);
    result.append(" iniFile: ").append(iniFile).append(NEW_LINE);
    int maxPrint = iniContent.length();
    if(maxPrint > 500) {
      maxPrint = 500;
    }
    result.append(" iniContent: ").append(iniContent.substring(0,maxPrint)).append("...[truncated@500]").append(NEW_LINE);
    result.append("}");
    return result.toString();
  }
}
