# RabbitMQ server install

1. AMQP server, This is a standard Ubuntu 14.04 system with the rabbitmq
server installed using the packages provided [here](https://www.rabbitmq.com/download.html)

2. Once installed run:


    sudo bash
    for i in rabbitmq_management rabbitmq_tracing rabbitmq_web_dispatch
      rabbitmq_web_stomp rabbitmq_management_agent
      rabbitmq_management_visualiser; do
        rabbitmq-plugins enable $i
    done

3. Create the needed accounts:


    rabbitmqctl add_user test test
    rabbitmqctl set_user_tags test administrator
    rabbitmqctl set_permissions -p / test ".*" ".*" ".*"

See the [manual](https://www.rabbitmq.com/man/rabbitmqctl.1.man.html) for additional detail.

4. Restart rabbitmq server and check you can connect to the web instance:


    http://localhost:15672

5. Modify the config for [`rabbit_host`](#RabbitMQconfig) to the full host name.

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
      - Gets and expands logs from the *.ERRORLOG queue to provided path (each host output to a folder named as the host)

The worker is executed on each host automatically with:

    ... config.cfg WORKER

# Base configuration options
You are able to have several instances of the `PRIMARY` process running, each
running on a different configuration set.  The important items to ensure are set correctly are:

For `config/XXXX.cfg` included in the command args:

* `Sanger.cfg` and `DKFZ.cfg` are included in the distribution
    * others will be added as they become available

## Parameters
The codebase will not run if the config file is readable by other users.  This is an attempt to prevent ssh and rabbitMQ account details being exposed.

In the included configuration an include is used to prevent developer credentials from being exposed.
In a production environment the values should be entered directly into the config file.

(please note the complete config is propagated to each host, permissions are set appropriately to protect content).

### SSH config
* `ssh_user` - user with appropriate access to login to all hosts listed in the file indicated in `workerCfg`
* `ssh_pw` - associated password

### RabbitMQ config
These parameters are essential for the use of the messaging service

* `rabbit_host` - address of the rabbitMQ server
* `rabbit_port` - port to use
* `rabbit_user` - ensure an account for the user has been specified, ability to create queues required (defaults)
* `rabbit_pw` - password for rabbit_user account

### proxies
Where applicable please configure proxies using these parameters in the standard format.  Comment individual elements if not needed.

* `http_proxy`
* `https_proxy`
* `no_proxy`

### image paths
These describe where you are able to write data to on your system.  Most of the workflows
assume the ability to write to the root folder, however these automatically repoint the
underlying codebases to use the defined base locations.

These paths must reflect the real locations, not those inside of a docker image (although they can match).

* `datastoreDir`
    * If your system allows you can just set this to `/datastore`
* `workflowDir`
    * If your system allows you can just set this to `/workflows`

### incoming ini
Ini files to be run against this workflow (not mixed) will be found in this folder.
The folder is regularly checked and any additions automatically added to the `*.PEND` queue.

This approach is allows new data from the central decider to be regularly added to the system without the need for restart.

* `wfl_inis` - A folder containing ini files for this workflow implemention.

### Permission keys
`gnosKeys` - Folder containing the following permission keys for GNOS servers

* ICGC.pem - Download/upload to ICGC GNOS servers
* TCGA.pem - Upload key for TCGA results.
* cghub.pem - Download key for TCGA BAMs

These need to be named like this to match the values returned by the central decider when generating ini files using the template model (see below).

### general
* `primaryLargeTmp` - You will need a large tmp area on the primary node.
* `qPrefix` - this sets the queue name prefix so that different workflows don't share queues.
    * Normally the workflow name, but if you have different versions extend this.

#### worker configs
It is unlikely that it will be necessary to modify these values.

* `log4-worker` - defines which log4j configuration to push to the worker node.
* `log4-delete` - Should correlate with the locations defined in the log4j config above.

#### provisioning info
These parameters will be rarely modified as they are specific for each workflow, versions may need bumping occasionally.

* `pullDockerImages` - csv of images that should be setup using `docker pull`.
* `curlDockerImages` - csv of images that should be setup using `docker load`.
* `seqware` - The seqware distribution (generally used only to unpack a workflow).
* `workflow` - The workflow to be unpacked and executed on the worker.

Any items with a remote URL (i.e. not a local file) are automatically downloaded and pushed to the workers (excluding `pullDockerImages`).


# Donor INI files and templates
Your ini files for the workflows should be created using the the [central-decider-client](https://github.com/ICGC-TCGA-PanCancer/central-decider-client).

Some items to note regarding pem paths in the different workflows, examples are in the template format:

* Sanger - pem paths are those within the docker image, i.e. always:
    * `pemFile=/workflow/[% download_gnos_key %].pem`
    * `uploadPemFile=/workflow/[% upload_gnos_key %].pem`
* DKFZ - pem paths are those of the base filesystem, if you have defined `workflowDir` as something other than '/workflows' these should reflect that difference.
    * `pemFile=/workflows/[% download_gnos_key %].pem`
    * `uploadPemFile=/workflows/[% upload_gnos_key %].pem`
    * `DKFZ.dkfzDataBundleDownloadKey=/workflows/ICGC.pem`

# Message queues
It is possible to have multiple 'primary' daemons running managing a different set of hosts (and workflows/versions) using the same message server.

All that is required is to ensure that the `qPrefix` is set differently for each logical group in `config.cfg`.

There are 10 core queues for each manager, these are:

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
* `*.UPLOADED`
    * Purely for book keeping, generally safe to purge.
    * Holds the workflow ini files for successfully completed work.
    * Header - none
    * Content - JSON `WorkflowIni.class`

In addition to these each host will have an activity queue under the same  `qPrefix`.

All queues are persistent and durable.

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
