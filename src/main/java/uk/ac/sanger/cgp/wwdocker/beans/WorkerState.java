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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.ac.sanger.cgp.wwdocker.actions.Utils;
import uk.ac.sanger.cgp.wwdocker.enums.HostStatus;

/**
 *
 * @author kr2
 */
public class WorkerState {
  private static final Logger logger = LogManager.getLogger();
  
  // digest of jar file
  private String md5Jar = new String();
  
  // digest of config file
  private String md5Config = new String();
  
  // status of worker
  private HostStatus status;
  
  // resources of worker
  private WorkerResources resource;
  
  public WorkerState() {
    // null constructor
    setResource(new WorkerResources());
  }
  
  public WorkerState(File jar, File config) {
    setMd5Jar(Utils.fileDigest(jar));
    setMd5Config(Utils.fileDigest(config));
    setResource(new WorkerResources());
  }

  /**
   * @return the md5Jar
   */
  public String getMd5Jar() {
    return md5Jar;
  }

  /**
   * @param md5Jar the md5Jar to set
   */
  public void setMd5Jar(String md5Jar) {
    this.md5Jar = md5Jar;
  }

  /**
   * @return the md5Config
   */
  public String getMd5Config() {
    return md5Config;
  }

  /**
   * @param md5Config the md5Config to set
   */
  public void setMd5Config(String md5Config) {
    this.md5Config = md5Config;
  }
  
  /**
   * @return the status
   */
  public HostStatus getStatus() {
    return status;
  }

  /**
   * @param status the status to set
   */
  public void setStatus(HostStatus status) {
    this.status = status;
  }
  
  /**
   * @return the resource
   */
  public WorkerResources getResource() {
    return resource;
  }

  /**
   * @param resource the resource to set
   */
  public void setResource(WorkerResources resource) {
    this.resource = resource;
  }

  public boolean equals(Object obj) {
    if(obj==null) { return false; }
    if(obj==this) { return true; }
    if (obj.getClass() != this.getClass()) {
      return false;
    }
    WorkerState in = (WorkerState) obj;
    if(in.getMd5Config().equals(md5Config) && in.getMd5Jar().equals(md5Jar)){
      return true;
    }
    return false;
  }
  
  public String toString() {
    StringBuilder result = new StringBuilder();
    String NEW_LINE = System.getProperty("line.separator");
    result.append(this.getClass().getName() + " Object {" + NEW_LINE);
    result.append(" md5Config: " + md5Config + NEW_LINE);
    result.append(" md5Jar: " + md5Jar + NEW_LINE);
    result.append(" status: " + status + NEW_LINE);
    result.append(" resource: " + resource + NEW_LINE);
    result.append("}");
    return result.toString();
  }
  
}
