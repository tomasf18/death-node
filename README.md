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
It offers insights into the rationale behind these choices, the project's architecture, and the impact of these decisions on the overall functionality and performance of the system.

This document presents installation and demonstration instructions.

All virtual machines are based on Debian 64-bit Linux, and the software stack includes:
- **Java 17+** and **Maven 3.x** for building and running the server and client applications. 
- **Docker** for running the **PostgreSQL** database container.
- **Python3** for the monitor to sniff packets. 
- **iptables** for configuring firewall rules and network forwarding between the server, clients, and monitor. 
- **Boot scripts** (`boot-config.sh`) for each machine, which configure static IP addresses, subnets, 
and firewall rules specific to each role (server, database, monitor).

## Installation

To see the project in action, it is necessary to setup a virtual environment, with 3 networks (sw0, sw1, sw2) and 5 machines:   
- Main server (sw0);
- Database (sw0);
- Monitor (sw0, sw1 and sw2);
- 2 clients (sw1 and sw2, one for each client).

The following diagram shows the networks and machines:

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

Verify installations:
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
2. Clone it, following this [instructions](https://github.com/tecnico-sec/Virtual-Networking?tab=readme-ov-file#21-clone-virtual-machines), to create the remaining machines:
   - **Server**
   - **Database**
   - **Monitor**
   - **Client1**
   - **Client2**
3. After cloning, update each network configuration using these [instructions](https://github.com/tecnico-sec/Virtual-Networking?tab=readme-ov-file#22a-virtualbox), and follow these requirements:
    - **Server and Database**: one adapter for `sw0`
    - **Monitor**: three adaptares, first for `sw0`, second for `sw1` and third for `sw2`
    - **Client1**: one adapter for `sw1`
    - **Client2**: one adapter for `sw2`

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
sudo ./boot-config.sh
```

Run the container:
```sh
cd deathnode-database
sudo ./setup-db.sh
```

To verify if the container is running:
```sh
sudo docker ps
```

Apply Database Schema:
```sh
psql -h localhost -U dn_admin -d deathnode -f server_schema.sql
```
> Password for user dn_admin: dn_pass

The expected results are:
- You see the list of databases, including `deathnode`.
- No connection errors.

### Machine 2 - Server
This machine runs the Deathnode application server using Spring Boot.

Run the boot script inside the `deathnode-server/` folder:
```sh
sudo ./boot-config.sh
```

Initialize Application Server:
```sh
mvn spring-boot:run
```

The expected results are:
- Application starts without errors.
- Connection to the database is successful.

### Machine 3 - Monitor
These machine run the monitor server, which serve as IDS and router at same time, to route packets 
from clients to the server and prevent spam attacks.

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
- Command line interface capturing all the traffic between the client and the server.

### Machine 4, 5 - Clients
These machines run the Deathnode clients, which communicate with the server.
The initialization is mostly the same; the only difference is the boot script used:
- **Client1** uses `boot1-config.sh`
- **Client2** uses `boot2-config.sh`

Initialize, run the respective boot script on each client inside th `deathnode-client/` folder:
```sh
sudo ./boot1-config.sh   # for Client 1
sudo ./boot2-config.sh   # for Client 2
```

Inside the client project directory run the client JAR with the appropriate arguments:
```sh
cd deathnode-client/
java -jar target/deathnode-client-1.0.0.jar "nodeA" "AlphaNode"   # Client 1
java -jar target/deathnode-client-1.0.0.jar "nodeB" "BetaNode"    # Client 2
```

The expected results are:
- Each client starts and registers itself with the server.
- No connection errors should appear in the console.
- Client can use the commands `create-reports` and most important sync them with the server using `sync` command

## Demonstration

Now that all networks and virtual machines are correctly configured and running, this section demonstrates the main features 
of the Deathnode system and highlights the security mechanisms in action.

All commands in this demonstration are executed on a client machine, while screenshots are provided to show the
corresponding outputs on the server and on the monitor (vigilant).

### Client Application Overview
The client application provides an interactive command-line interface. It can be launched as follows:

```sh
cd deathnode-client/
java -jar target/deathnode-client-1.0.0.jar "nodeA" "AlphaNode"
```
Upon startup, the client connects to the server through the monitor and waits for user commands.

<p align="center">
  <img src="resources/img/server_init.png" width="35%" />
  <img src="resources/img/client_init.png" width="40%" />
</p>
<p align="center">
  <em>Server with the client connection (left) and client with cli connected successfully with the server (right)</em>
</p>

### Help Command
The `help` command lists all available client operations.

<p align="center">
  <img src="resources/img/client_help.png" width="50%" />
</p>
<p align="center">
  <em>The client prints the list of supported commands and their usage.</em>
</p>

### Creating a Report
The `create-report` command allows a client to create a new report providing:
`Suspect`; `Description` and `Location`. After the envelope is created and stored in buffer, outputting the name and the
local seq number

<p align="center">
  <img src="resources/img/create-report.png" width="100%" />
</p>
<p align="center">
  <em>The client prints the created report</em>
</p>

### Creating Random Reports (Simulated Flood)
The `create-random` command generates multiple reports automatically. This command is used to simulate abusive
behavior and trigger the security mechanisms. The command asks for one input, the number of reports to generate.



This concludes the demonstration.

## Additional Information

### Links to Used Tools and Libraries

- [Java 11.0.16.1](https://openjdk.java.net/)
- [Maven 3.9.5](https://maven.apache.org/)
- ...

### Versioning

We use [SemVer](http://semver.org/) for versioning.  

### License

This project is licensed under the MIT License - see the [LICENSE.txt](LICENSE.txt) for details.

*(switch to another license, or no license, as you see fit)*

----
END OF README
