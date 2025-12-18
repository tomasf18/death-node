# Install docker
sudo apt install -y docker.io
sudo systemctl enable docker
sudo systemctl start docker

sudo docker pull postgres:18
sudo docker pull alpine:latest

# Install Java & Maven
sudo apt install -y openjdk-17-jdk
sudo apt install -y maven

# Install python3 and the require package for the monitor
sudo apt install python3
sudo apt install python3-scapy -y