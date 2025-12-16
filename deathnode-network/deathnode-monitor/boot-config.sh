#!/bin/bash

IFACE_SERVER=eth0
IFACE_CLIENT1=eth1
IFACE_CLIENT2=eth2

NETMASK=255.255.255.0
PORT=9090
SERVER_IP=192.168.0.10
CLIENT_IP1=192.168.1.100
CLIENT_IP2=192.168.2.100

# set static ip to the server network
sudo ifconfig $IFACE_SERVER 192.168.0.254 netmask $NETMASK up
# client1 network
sudo ifconfig $IFACE_CLIENT1 192.168.1.254 netmask $NETMASK up
# client2 network
sudo ifconfig $IFACE_CLIENT2 192.168.2.254 netmask $NETMASK up

# Persist in the file /etc/network/interfaces.d/monitor.cfg
cat <<EOF | sudo tee /etc/network/interfaces.d/monitor.cfg
auto $IFACE_CLIENT1
iface $IFACE_CLIENT1 inet static
    address $CLIENT_IP1
    netmask $NETMASK

auto $IFACE_CLIENT2
iface $IFACE_CLIENT2 inet static
    address $CLIENT_IP2
    netmask $NETMASK

auto $IFACE_SERVER
iface $IFACE_SERVER inet static
    address $IP_SERVER
    netmask $NETMASK
EOF

# Restart the network to apply the configurations
sudo systemctl restart NetworkManager

# IP FORWARDING
sudo sysctl net.ipv4.ip_forward=1
echo "net.ipv4.ip_forward=1" | sudo tee /etc/sysctl.d/99-monitor-forward.conf
sudo iptables -P FORWARD ACCEPT    # Defines default policy for FORWARD
sudo iptables -F FORWARD           # Flushes all the rules from chain FORWARD

###########
# Firewall
###########
# Check if UFW is installed, install if missing
if ! command -v ufw &> /dev/null; then
    sudo apt update
    sudo apt install ufw -y
fi

# Enable UFW
sudo ufw reset
sudo ufw --force enable
sudo ufw default deny incoming
sudo ufw default allow outgoing

# Rules
sudo ufw allow $PORT/tcp
sudo ufw reload
