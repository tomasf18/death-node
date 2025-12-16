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

# Restart the network to apply the configurations
sudo systemctl restart NetworkManager

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

# Rules
sudo ufw allow from $GATEWAY to any port $PORT proto tcp
sudo ufw allow out to $DB_IP port $DB_PORT proto tcp
sudo ufw reload
