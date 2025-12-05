# server and database
  
## requirements
- Docker
- Docker Compose

## Running the Server and Database
To run the DeathNode server along with the PostgreSQL database, you can use Docker Compose.
1. Ensure you have Docker and Docker Compose installed on your machine.
2. Navigate to the root directory, containing the `compose.yml` file.
3. Run the following command to start the services:

```sh
docker compose up -d --build # you can omit -d to see the container logs
```

## Accessing the Database
To access the PostgreSQL database, you can use the `psql` command-line tool. If you have `psql` installed on your host machine, you can connect to the database with the following command:

```sh
psql -h localhost -U dn_admin -d deathnode
```

Then enter the password `dn_pass` when prompted.

Parameters:
- `-h localhost`: Specifies the host where the database is running.
- `-U dn_admin`: Specifies the username to connect with.
- `-d deathnode`: Specifies the database name to connect to.

