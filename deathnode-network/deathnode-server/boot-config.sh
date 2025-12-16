#!/bin/bash

IP=192.168.0.10
NETMASK=255.255.255.0
GATEWAY=192.168.0.254
IFACE=eth0
PORT=9090
DB_IP=192.168.0.200
DB_PORT=5432

# set static ip to the server
sudo ifconfig $IFACE $IP netmask $NETMASK up

IP_ASSIGNED=$(ifconfig eth0 | grep 'inet ' | awk '{print $2}')
if [ "$IP_ASSIGNED" == "$IP" ]; then
    echo "IP assigned successfully: $IP_ASSIGNED"
else
    echo "ERROR attributing the IP, restarting the NetworkManager"
    sudo systemctl restart NetworkManager
fi

# Persist in the file /etc/network/interfaces.d/server.cfg
sudo tee /etc/network/interfaces.d/server.cfg > /dev/null <<EOF
auto $IFACE
iface $IFACE inet static
    address $IP
    netmask $NETMASK
    gateway $GATEWAY
EOF

sudo ip route add default via $GATEWAY

# Restart the network to apply the configurations
sudo systemctl restart NetworkManager

###########
# Firewall
###########

# Clean old firewall rules
sudo iptables -F
sudo iptables -X

# Default
sudo iptables -P INPUT DROP
sudo iptables -P OUTPUT ACCEPT
sudo iptables -P FORWARD DROP

# Loopback
sudo iptables -A INPUT -i lo -j ACCEPT

# Allow port 9090 only to the monitor
sudo iptables -A INPUT -p tcp -s $GATEWAY --dport $PORT -j ACCEPT

# Allow DB communications
sudo iptables -A OUTPUT -p tcp -d $DB_IP --dport $DB_PORT -j ACCEPT
sudo iptables -A INPUT -p tcp -s $DB_IP --sport $DB_PORT -j ACCEPT

# Save rules
sudo iptables-save | sudo tee /etc/iptables/rules.v4

