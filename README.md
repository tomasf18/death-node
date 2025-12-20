# A53 DeathNode

## Team

| Number | Name           | User                                      | E-mail                                          |
|--------|----------------|-------------------------------------------|-------------------------------------------------|
| 116496 | Guilherme Pais | <https://github.com/Guilherme-Parentesco> | <mailto:guilherme.p.pais@tecnico.ulisboa.pt>    |
| 116122 | Tomás Santos   | <https://github.com/tomasf18>             | <mailto:tomas.santos.f@tecnico.ulisboa.pt>      |
| 116390 | Pedro Duarte   | <https://github.com/pedropmad>            | <mailto:pedro.alegre.duarte@tecnico.ulisboa.pt> |

![Guilherme Pais](resources/img/Guilherme.png) ![Tomás Santos](resources/img/Tomas.jpg) ![Pedro Duarte](resources/img/Pedro.jpg)


## Contents

This repository contains documentation and source code for the *Network and Computer Security (SIRS)* project scenario **DeathNode**.

The [REPORT](./docs/REPORT.md) document provides a detailed overview of the key technical decisions and various components of the implemented project.
It offers insights into the rationale behind these choices, the project's architecture, and the impact of these decisions
on the overall functionality and performance of the system.

This document presents installation and demonstration instructions.

All virtual machines are based on Debian 64-bit Linux, and the software stack includes:
- **Java 17+** and **Maven 3.x** for building and running the server and nodes applications. 
- **Docker** for running the **PostgreSQL** database container.
- **Python3** for the monitor to sniff packets. 
- **iptables** for configuring firewall rules and network forwarding between server, nodes, and monitor. 
- **Boot scripts** (`boot-config.sh`) for each machine, which configure static IP addresses, subnets, 
and firewall rules specific to each role (server, database, monitor).

## Installation

To see the project in action, it is necessary to set up a virtual environment, with 3 networks (sw0, sw1, sw2) and 5 machines:   
- Main server (sw0);
- Database (sw0);
- Monitor (sw0, sw1 and sw2);
- 2 clients (sw1 and sw2, one for each client).

#### The following diagram shows the networks and machines:

![](resources/img/Network_Diagram.png)

## Prerequisites

All the virtual machines are based on: Linux debian 64-bit 

**Download the official Kali Linux distribution:**
- **Virtual Box**: [Download](hhttps://cdimage.kali.org/kali-2025.4/kali-linux-2025.4-virtualbox-amd64.7z) and [Instructions](https://www.kali.org/docs/virtualization/import-premade-virtualbox/)
- **VMware**: [Download](https://cdimage.kali.org/kali-2025.4/kali-linux-2025.4-vmware-amd64.7z) and [Instructions](https://www.kali.org/docs/virtualization/import-premade-vmware/)

**After the base virtual machine is installed and running, update the system:**
```sh
sudo apt update && sudo apt upgrade -y
```

Install the required base packages:
```sh
sudo apt install -y \
  ca-certificates \
  curl \
  gnupg \
  lsb-release \
  git
```

Also have to clone the project, and enter in the `deathnode-network` folder:
```sh
git clone https://github.com/tecnico-sec/A53-DeathNode.git
cd A53-DeathNode/deathnode-network
```

Use the script `requirements.sh` to install the extra requirements
to be able to run correctly the system:
```sh
chmod +x requirements.sh
./requirements.sh
```

Verify the installations:
```sh
java -version
mvn -version
docker --version
pytohn3 --version
```

Lastly, compile the maven project:
```sh
mvn clean install
```

**Once the base virtual machine is fully configured:**
1. Power off the VM
2. Clone it, following these [instructions](https://github.com/tecnico-sec/Virtual-Networking?tab=readme-ov-file#21-clone-virtual-machines), to create the remaining machines:
   - **Server**
   - **Database**
   - **Monitor**
   - **NodeA**
   - **NodeB**
3. After cloning, update each network configuration using these [instructions](https://github.com/tecnico-sec/Virtual-Networking?tab=readme-ov-file#22a-virtualbox), and follow these requirements:
    - **Server and Database**: one adapter for `sw0`
    - **Monitor**: three adaptares, first for `sw0`, second for `sw1` and third for `sw2`
    - **NodeA**: one adapter for `sw1`
    - **NodeB**: one adapter for `sw2`

## Machine configurations

For each machine, there is a **boot script** named `boot-config.sh` that configures the network and firewall rules specific
to that machine. This script sets a static IP in the appropriate subnet and applies firewall rules required
for the machine to communicate with the others.

More detailed information about these rules is available in [REPORT.md](./docs/REPORT.md).

Next we have custom instructions for each machine.

### Machine 1 - Database Server

This machine runs a PostgreSQL 18.0 database server inside a Docker container.

Run the boot script inside the `deathnode-database/` folder:
```sh
cd deathnode-database
sudo ./boot-config.sh
```

Run the container:
```sh
sudo ./setup-db.sh
```

To verify if the container is running:
```sh
sudo docker ps
```

The expected results are:
- No connection errors.
- Container running with postgreSQL:18

### Machine 2 - Server
This machine runs the Deathnode application server using Spring Boot.

Run the boot script inside the `deathnode-server/` folder:
```sh
cd deathnode-server
sudo ./boot-config.sh
```

Try to communicate with the DB server and apply Database Schema:
```sh
psql -h 192.168.0.200 -U dn_admin -d deathnode -f ../deathnode-database/server_schema.sql
```
> Password for user dn_admin: dn_pass

Initialize Application Server:
```sh
mvn spring-boot:run
```

The expected results are:
- Application starts without errors.
- Connection to the database is successful.

### Machine 3 - Monitor
These machine run the monitor server, which serve as IDS and router at same time, to route packets 
from nodes to the server and prevent spam attacks.

The initialization is the same, run the file `boot-config` in the folder `deathnode-monitor`:
```sh
cd deathnode-monitor
./boot-config
```

After the initialization, run the python script in the same folder to start monitoring packets:
```sh
python3 monitor.py
```

The expected results are:
- Command line interface capturing all the traffic between the nodes and the server.

### Machine 4, 5 - Nodes
These machines run the DeathNode nodes, which communicate with the server.
The initialization is mostly the same; the only difference is the boot script used:
- **NodeA** uses `boot1-config.sh`
- **NodeB** uses `boot2-config.sh`

Initialize, run the respective boot script on each client inside the `deathnode-client/` folder:
```sh
cd deathnode-client
sudo ./boot1-config.sh   # for Client 1
sudo ./boot2-config.sh   # for Client 2
```

Inside the client project directory run the client JAR with the appropriate arguments:
```sh
java -jar target/deathnode-client-1.0.0.jar "nodeA" "AlphaNode"   # Node 1
java -jar target/deathnode-client-1.0.0.jar "nodeB" "BetaNode"    # Node 2
```

The expected results are:
- Each client starts and registers itself with the server.
- No connection errors should appear in the console.
- Client can use the commands `create-reports` and most important sync them with the server using the `sync` command

## Demonstration

Now that all networks and virtual machines are correctly configured and running, this section demonstrates the main features 
of the DeathNode system and highlights the security mechanisms in action.

All commands in this demonstration are executed on a client machine, while screenshots are provided to show the
corresponding outputs on the server and on the monitor (vigilant).

### Node Application Overview
The node application provides an interactive command-line interface. It can be launched as follows:

```sh
cd deathnode-client/
java -jar target/deathnode-client-1.0.0.jar "nodeA" "AlphaNode"
```
Upon startup, the node connects to the server through the monitor and waits for user commands.

<p align="center">
  <img src="resources/img/server_init.png" width="25%" style="margin-right: 5rem"/>
  <img src="resources/img/node-init.png" width="30%" />
</p>
<p align="center">
     <img src="resources/img/monitor-init.png" width="35%" />
</p>
<p align="center">
  <em>Server with the client connection (left) and NodeA connected successfully with the Server (right) <br>This monitor displays the first sync request from Node A during its initial connection to the server (bottom
</em>
</p>


### Creating a Report
The `create-report` command allows a node to create a new report providing:
`Suspect`; `Description` and `Location`. After the envelope is created and stored in buffer, outputting the name and the
local seq number

<p align="center">
  <img src="resources/img/create-report.png" width="50%" />
</p>
<p align="center">
  <em>The client prints the created report</em>
</p>

After the threshold is achieved of the buffer size, the sync is requested from the NodeA and the server initiate the `sync round` for all Nodes in the network, if no other round is active.
<p align="center">
  <img src="resources/img/node-trigger-sync.png" width="35%" style="margin-right: 5rem"/>
  <img src="resources/img/server-sync-round.png" width="40%" />
</p>
<p align="center">
     <img src="resources/img/wire-syncRequest.png" width="50%" />
</p>
<p align="center">
  <em>The NodeA triggers the sync process and subsequently receives the ordered report from the server (left) and the server receives the sync request, processes it correctly, and sends a response back to the NodeA. (right)<br>
The Wireshark application shows the packet capture during sync, demonstrating that the communication is encrypted using TLS and that the content is not disclosed (bottom)
</em>
</p>

Listing the reports, using the command `list-reports` we can see if the report is sync with the server and with the others Nodes,
by verifying the `global_seq` attribute:
<p align="center">
  <img src="resources/img/list-reports.png" width="60%" />
</p>


### Creating Random Reports (Simulated Flood)
The `create-random` command generates multiple reports automatically. This command is used to simulate abusive
behavior and trigger the security mechanisms. The command asks for one input, the number of reports to generate.

<p align="center">
   <img src="resources/img/create-random.png" width="40%" style="margin-right: 5rem"/>
   <img src="resources/img/monitor-block-Node.png" width="40%" />
</p>
<p align="center">
<em>The NodeA is using an abusive command and sending multiple synchronization requests in a spam-like manner. (left)<br>
The monitor blocks the Node's requests and adds a temporary firewall rule to drop packets from the abusive Node.
If the abusive behavior continues after the timeout, the duration of the blocking rule is progressively increased (right)
</em>
</p>

### Security measures
One of the most important requirements in our system is information integrity, and order. With the development of our Secure Document Tool,
whenever any byte of information is modified, the other nodes are able to detect that the integrity has been compromised.

When we change, for example, the `report_id` in the `metadata` and attempt to sync with the server,
the synchronization proceeds normally, but the other nodes can detect that the report is invalid.

<p align="center">
   <img src="resources/img/report-modified.png" width="45%" style="margin-right: 2rem"/>
   <img src="resources/img/integrity.png" width="50%" />
</p>
<p align="center">
<em>The unaltered report, where we will change only the report_id or another attribute. (left)
and when performing list-reports, a hash mismatch error is displayed. (right)</em>
</p>

In addition to integrity, we also demonstrate how the nodes can detect `if the order of reports has been altered` and cancel the corresponding synchronization round.
<p align="center">
  <img src="resources/img/error-order.png" width="60%" />
</p>

This concludes the demonstration.

## Additional Information

### Links to Used Tools and Libraries

- [Java 21.0](https://openjdk.org/projects/jdk/21/)
- [Maven 3.9.5](https://maven.apache.org/)
- [Python 3.14.2](https://docs.python.org/3/)
- [Java Security](https://docs.oracle.com/en/java/javase/25/security/java-security-overview1.html)
- [Virtual Box](https://www.virtualbox.org/)
- [Docker](https://docs.docker.com/)
- [PostgreSQL 18.0](https://www.postgresql.org/docs/18/index.html)

----
END OF README
