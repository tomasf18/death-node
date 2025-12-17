#!/bin/bash

IP=192.168.2.100
NETMASK=255.255.255.0
GATEWAY=192.168.2.254
IFACE=eth0

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