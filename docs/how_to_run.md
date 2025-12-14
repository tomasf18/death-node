# database

docker run -d --name deathnode-db -e POSTGRES_DB=deathnode -e POSTGRES_USER=dn_admin -e POSTGRES_PASSWORD=dn_pass -p 5432:5432 postgres:18

# at deathnode-network/

mvn clean install

# sever 

mvn spring-boot:run

# client 

java -jar target/deathnode-client-1.0.0.jar