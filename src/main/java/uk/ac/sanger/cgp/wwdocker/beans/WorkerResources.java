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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.management.OperatingSystemMXBean;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author kr2
 */
public class WorkerResources {
  private static final Logger logger = LogManager.getLogger();
  // Property sysLoadAvg
  private double sysLoadAvg;

  // Property availableProcessors
  private int availableProcessors = -1;

  // Property totalMemBytes
  private long totalMemBytes;

  // Property freeMemBytes
  private long freeMemBytes;
  
  // Property hostName
  private String hostName;
  
  private Long pid;
  
  // Property hostStatus
  Enum hostStatus; // this will be determined based on md5 sums of config and jar file
  
  /** 
   * Constructor
   * @return HostResources
   */
  public WorkerResources() {
    init();
  }
  
  public void init() {
    OperatingSystemMXBean osmxb = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
    if(availableProcessors == -1) {
      base_init(osmxb);
    }
    sysLoadAvg = osmxb.getSystemLoadAverage();
    freeMemBytes = osmxb.getFreePhysicalMemorySize();
  }
  
  private void base_init(OperatingSystemMXBean osmxb) {
    String [] bits = ManagementFactory.getRuntimeMXBean().getName().split("@");
    pid = Long.getLong(bits[0]);
    totalMemBytes = osmxb.getTotalPhysicalMemorySize();
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
   * @return the pid
   */
  public Long getPid() {
    return pid;
  }

  /**
   * Simple toString
   * @return 
   */
  @Override
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
    if(pid == null) {
      result.append(" pid: null" + NEW_LINE);
    }
    else {
      result.append(" pid: " + pid.toString() + NEW_LINE);
    }
    result.append("}");

    return result.toString();
  }
}
