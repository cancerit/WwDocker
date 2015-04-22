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

package uk.ac.sanger.cgp.wwdocker.factories;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import uk.ac.sanger.cgp.wwdocker.interfaces.Workflow;
import uk.ac.sanger.cgp.wwdocker.workflow.SangerWorkflow;

/**
 *
 * @author kr2
 */
public class WorkflowFactory {
  private static final String WORKFLOW_PREFIX = "Workflow_Bundle_";
  private static final Logger logger = LogManager.getLogger();
  
  public Workflow getWorkflow(PropertiesConfiguration config) {
    String workflowFile = config.getString("workflow");
    int startPos = workflowFile.lastIndexOf(WORKFLOW_PREFIX) + WORKFLOW_PREFIX.length();
    logger.trace(workflowFile);
    int endPos = workflowFile.indexOf("_", startPos);
    String workflow = workflowFile.substring(startPos, endPos);
    logger.trace("Got workflow: "+ workflow);
    
    switch(workflow) {
      case "SangerPancancerCgpCnIndelSnvStr":
        logger.trace("Creating a SangerWorkflow manager");
        return new SangerWorkflow(config);
      default:
        throw new RuntimeException("Workflow filename doesn't decode to a known workflow type:" + workflow);
    }
  }
}
