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

package uk.ac.sanger.cgp.wwdocker.beans;

import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONObject;
import uk.ac.sanger.cgp.wwdocker.interfaces.HostInfo;

/**
 *
 * @author kr2
 */
public class HostResources implements HostInfo {
  private static final Logger logger = LogManager.getLogger();
  // Property sysLoadAvg
  double sysLoadAvg;

  // Property availableProcessors
  int availableProcessors;

  // Property totalMemBytes
  long totalMemBytes;

  // Property freeMemBytes
  long freeMemBytes;
  
  // Property hostName
  String hostName;
  
  /** 
   * Constructor
   * @return HostResources
   */
  public HostResources() {
    OperatingSystemMXBean osmxb = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    sysLoadAvg = osmxb.getSystemLoadAverage();
    totalMemBytes = osmxb.getTotalPhysicalMemorySize();
    freeMemBytes = osmxb.getFreePhysicalMemorySize();
    availableProcessors = Runtime.getRuntime().availableProcessors();
    hostName = System.getenv("HOSTNAME");
    if(hostName == null) {
      try {
        hostName = InetAddress.getLocalHost().getHostName();
      }
      catch(UnknownHostException e) {
        throw new RuntimeException("Unable to determine hostname", e);
      }
    }
  }
  
  public HostResources(String json) {
    JSONObject jo = new JSONObject(json);
    sysLoadAvg = jo.getDouble("sysLoadAvg");
    totalMemBytes = jo.getLong("totalMemBytes");
    freeMemBytes =  jo.getLong("freeMemBytes");
    availableProcessors = jo.getInt("availableProcessors");
    hostName = jo.getString("hostName");
  }
  
  /**
   * Gets the sysLoadAvg
   */
  public double getSysLoadAvg() {
    return this.sysLoadAvg;
  }

  /**
   * Gets the availableProcessors
   */
  public int getAvailableProcessors() {
    return this.availableProcessors;
  }

  /**
   * Gets the totalMemBytes
   */
  public long getTotalMemBytes() {
    return this.totalMemBytes;
  }

  /**
   * Gets the freeMemBytes
   */
  public long getFreeMemBytes() {
    return this.freeMemBytes;
  }
  
  /**
   * Gets the hostName
   */
  public String getHostName() {
    return this.hostName;
  }
  
  /**
   * Gets the JSON string for this object
   */
  public String toJson() {
    JSONObject jsonObj = new JSONObject( this );
    return jsonObj.toString();
  }
  
  /**
  * Simple toString
  */
  public String toString() {
    StringBuilder result = new StringBuilder();
    String NEW_LINE = System.getProperty("line.separator");
//{"hostName":"mib105183i.local","freeMemBytes":10240000,"availableProcessors":4,"sysLoadAvg":1.8759765625,"totalMemBytes":17179869184}
    result.append(this.getClass().getName() + " Object {" + NEW_LINE);
    result.append(" hostName: " + hostName + NEW_LINE);
    result.append(" freeMemBytes: " + freeMemBytes + NEW_LINE);
    result.append(" availableProcessors: " + availableProcessors + NEW_LINE );
    result.append(" sysLoadAvg: " + sysLoadAvg + NEW_LINE);
    result.append(" totalMemBytes: " + totalMemBytes + NEW_LINE);
    result.append("}");

    return result.toString();
  }
}
