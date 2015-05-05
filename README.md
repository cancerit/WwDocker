THIS IS A WORK IN PROGRESS
==========================

# Basic execution
After compile the code can be run using the following:

```
java -Dlog4j.configurationFile="config/log4j.properties.xml" -jar target/WwDocker-0.1.jar config/workflow.cfg Primary
```

(substitute the relevant workflow config file)

This will provision all hosts listed in the file indicated by the config value 'workerCfg' defined in `config/workflow.cfg`.
Once provisioned a worker daemon will be started on the host and await work from the message queue `*.PEND`.

Usage is available when executed with no arguments following the `*.jar` element of the command (please refer to that for most up to date details):


    The following are valid usage patterns:

      ... config.cfg PRIMARY
          - Starts the 'head' node daemon which provisions and monitors workers

      ... config.cfg PRIMARY KILLALL
        - Issues KILL message to all hosts listed in the workers.cfg file

      ... config.cfg ERRORS /some/path
        - Gets and expands logs from the *.ERRORLOG queue

The worker is executed on each host automatically with:

    ... config.cfg WORKER

# Message queues
It is possible to have multiple 'primary' daemons running managing a different set of hosts (and workflows/versions) using the same message server.

All that is required is to ensure that the `qPrefix` is set differently for each logical group in `config.cfg`.

There are 9 core queues for each manager, these are:

* `*.ACTIVE`
    * Responses from hosts to status requests are sent to this queue.
    * Header - host=hostName
    * Content - JSON `WorkerState.class`
* `*.BROKEN`
    * Hosts that fail to start are registered here, remove from workers.cfg and then re-add to retry.
    * Header - host=hostName
    * Content - JSON `WorkerState.class`
* `*.CLEAN`
    * Hosts awaiting work show here - this has no function other than to show they are available.
    * Header - host=hostName
    * Content - JSON `WorkerState.class`
* `*.DONE`
    * Stub for off-line mode - transient changes to `*.CLEAN` almost instantly, state would remain until result data retrieved.
    * Header - host=hostName
    * Content - JSON `WorkerState.class`
* `*.ERROR`
    * Hosts that encountered an error during processing register here.
    * Header - host=hostName
    * Content - JSON `WorkerState.class`
* `*.ERRORLOGS`
    * Correlates with `*.ERROR`, holds tar.gz of logs and seqware scripts (see `ERRORS` usage pattern).
    * Header - host=hostName
    * Content - Binary - tar.gz
* `*.PEND`
    * Holds the workflow ini files currently pending.
    * Header - none
    * Content - JSON `WorkflowIni.class`
* `*.RECEIVE`
    * Stub for off-line mode - Hosts undergoing data transfer for workflow would be listed here.
    * Header - host=hostName
    * Content - JSON `WorkerState.class`
* `*.RUNNING`
    * List of hosts currently running a workflow.
    * Header - host=hostName
    * Content - JSON `WorkerState.class`

# Multiple workflows (and/or workflow versions)
You are able to have several instances of the `PRIMARY` process running, each
running on a different configuration set.  The important items to ensure are set correctly are:

* For `config.cfg` (e.g. `Sanger.ini`) included on in the command args:
    * `qPrefix` - this sets the queue prefix so that different workflows don't share queues
    * `workerCfg` - this needs to point to a different list of hosts to any other running workflows
    * `workflow` - update this to the version you want

----

LICENSE
=======
Copyright (c) 2015 Genome Research Ltd.

Author: Cancer Genome Project cgpit@sanger.ac.uk

This file is part of WwDocker.

WwDocker is free software: you can redistribute it and/or modify it under
the terms of the GNU Affero General Public License as published by the Free
Software Foundation; either version 3 of the License, or (at your option) any
later version.

This program is distributed in the hope that it will be useful, but WITHOUT
ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see <http://www.gnu.org/licenses/>.

1. The usage of a range of years within a copyright statement contained within
this distribution should be interpreted as being equivalent to a list of years
including the first and last year specified and all consecutive years between
them. For example, a copyright statement that reads 'Copyright (c) 2005, 2007-
2009, 2011-2012' should be interpreted as being identical to a statement that
reads 'Copyright (c) 2005, 2007, 2008, 2009, 2011, 2012' and a copyright
statement that reads "Copyright (c) 2005-2012' should be interpreted as being
identical to a statement that reads 'Copyright (c) 2005, 2006, 2007, 2008,
2009, 2010, 2011, 2012'."

----
