#!/bin/bash

IP=192.168.0.200
NETMASK=255.255.255.0
IFACE=eth0
PORT=5432
SERVER_IP=192.168.0.10

# set static ip to the DB
sudo ifconfig $IFACE $IP netmask $NETMASK up

IP_ASSIGNED=$(ifconfig $IFACE | grep 'inet ' | awk '{print $2}')
if [ "$IP_ASSIGNED" == "$IP" ]; then
    echo "IP assigned successfully: $IP_ASSIGNED"
else
    echo "ERROR attributing the IP, restarting the NetworkManager"
    sudo systemctl restart NetworkManager
fi

# Persist in the file /etc/network/interfaces.d/DB.cfg
sudo tee /etc/network/interfaces.d/DB.cfg > /dev/null <<EOF
auto $IFACE
iface $IFACE inet static
    address $IP
    netmask $NETMASK
EOF

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

# Allow DB communications
sudo iptables -A INPUT -p tcp -s $SERVER_IP --dport $PORT -j ACCEPT
sudo iptables -A OUTPUT -p tcp -d $SERVER_IP --sport $PORT -j ACCEPT

# Save rules
sudo iptables-save | sudo tee /etc/iptables/rules.v4