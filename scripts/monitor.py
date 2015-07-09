import subprocess, json, tempfile, os

#Run as the CGPPIPE user

docker = "docker"
moniter = "WwDocker-"
strings = (docker, moniter)

dsh_path = "/nfs/users/nfs_c/cgppipe/.dsh/group/cgp5"

class Moniter(object):
    def __init__(self):
        pass

    def run(self):
        #Get the information
        cluster_dict = self.find_docker()
        #Parse it
        node_data = self.parse_cluster(cluster_dict)
        #Continue parsing working nodes
        self.parse_working_nodes(node_data["working"])
        self.parse_working_nodes(node_data["headless hosts"])
        #Continue parsing hidden nodes
        self.parse_hidden_nodes(node_data["hidden"])
        #Output
        return json.dumps(node_data, indent = 4, sort_keys = True)

    def find_docker(self):
        cmd = "dsh -Mg cgp5 'ps -fu cgppipe'"
        sub = subprocess.Popen(cmd, shell=True,
                               stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE)
        #Fill the output dict with every farm node
        nodes = {}
        node_names = open(dsh_path)
        for name in node_names:
            nodes[name.rstrip()] = []
        node_names.close()
        #Wait until cmd is finished and zip with the appropriate function
        buffers = zip(sub.communicate(), (self.process_stdout, self.process_stderr))
        for buf in buffers:
            for line in buf[0].split("\n"):
                node, prog = buf[1](line)
                if node is not None:
                    nodes[node].append(prog)
        return nodes

    def parse_cluster(self, cluster):
        node_data = {"idle hosts": {},
                     "problem hosts": {},
                     "headless hosts": {},
                     "working": {},
                     "hidden": {}}
        for node_id, node in cluster.iteritems():
            if node == []: #None of the servives running
                node_data["hidden"][node_id] = ""
            elif node == [docker]: #Only the docker script is running
                node_data["headless hosts"][node_id] = ""
            elif node == [moniter]: #Only the moniter is running
                node_data["idle hosts"][node_id] = ""
            elif sorted(node) == [moniter, docker]: #Both are running
                node_data["working"][node_id] = ""
            else: #The only other possability is there's an error
                if node[0][:5] == "error":
                    node_data["problem hosts"][node_id] = node[0].split(":", 2)[2][1:]
                else:
                    node_data["problem hosts"][node_id] = "Running unexpected set of programs: %s"%node
        return node_data

    def parse_working_nodes(self, nodes):
        #Find the .ini file
        cmd = "dsh -f %s -M 'ls -l /cgp/datastore/*.ini'"
        stdout_buf = self.parse_nodes(nodes.keys(), cmd)
        for node in stdout_buf.rstrip().split("\n"):
            node_id, info = node.split(":", 1)
            nodes[node_id] = info[1:]

    def parse_hidden_nodes(self, hidden_nodes):
        #Parse the nodes that aren't running either the docker or the monitering system
        cmd = "dsh -f %s -M 'grep -Fc \"UPLOADED FILE AFTER \" /cgp/datastore/oozie-*/generated-scripts/*vcfUpload_*.stdout'"
        #Run the command
        stdout_buf, stderr_buf = self.parse_nodes(hidden_nodes.keys(), cmd, no_stderr = False)
        #If the string 'UPLOADED FILE AFTER ' wasn't found, further analysis is necessary
        for node in self.get_nodes(stderr_buf):
            hidden_nodes[node] = "Further analysis necessary"
        #For every other node, get the log data
        nodes = self.get_nodes(stdout_buf)
        cmd = "dsh -f %s -M 'ls /cgp/datastore/*.ini'"
        stdout_buf = self.parse_nodes(nodes, cmd)
        for node_info in stdout_buf.rstrip().split("\n"):
            node, info = node_info.split(":")
            hidden_nodes[node] = info[1:]
    
    def get_nodes(self, out):
        #Returns the names of nodes in a list of nodes
        return map(lambda node: node.split(":")[0], out.rstrip().split("\n"))

    def parse_nodes(self, nodes, raw_cmd, no_stderr = True):
        tmp_name = self.make_tmp_file("\n".join(nodes))
        cmd = raw_cmd%tmp_name
        sub = subprocess.Popen(cmd, shell=True,
                               stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE)
        #Blocking, collects stdout and stderr
        buffer = sub.communicate()
        #Remove the temp file
        os.remove(tmp_name)
        if no_stderr:
            assert(buffer[1] == "")
            return buffer[0]
        return buffer

    def process_stdout(self, stdout):
        for string in strings:
            #If the given string is found in the line
            if stdout.find(string) != -1:
                #Return the node and the string found
                return stdout.split(":")[0], string
        return None, None

    def process_stderr(self, stderr):
        #Return the node and the error message
        if stderr == "": return None, None
        return stderr.split(":")[0], "error: " + stderr

    def make_tmp_file(self, contents):
        #Create a temp file and write to it the contents given and return its name
        tmpfile = tempfile.NamedTemporaryFile(prefix = "docker_moniter_", delete=False)
        tmpfile.write(contents)
        tmp_name = tmpfile.name
        tmpfile.close()
        return tmp_name

def main():
    print Moniter().run()

if __name__ == "__main__":
    main()

