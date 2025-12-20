## How to run with current configuration

### 1. Build the Project

```bash
cd deathnode-network/
mvn clean install
```

### 2. Run the Database

```bash
docker stop deathnode-db
docker rm deathnode-db
docker volume rm deathnode-db-certs

chmod 600 deathnode-database/keys/tls-key.pem
chmod 644 deathnode-database/keys/tls-cert.pem
chmod 644 deathnode-database/keys/ca-cert.pem

# Create a temporary container to copy files into the volume
docker volume create deathnode-db-certs
docker run --rm -v deathnode-db-certs:/certs -v $(pwd)/deathnode-database/keys:/src alpine sh -c \
   "cp /src/tls-cert.pem /certs/server.crt && \
   cp /src/tls-key.pem /certs/server.key && \
   cp /src/ca-cert.pem /certs/root.crt && \
   chown 999:999 /certs/* && \
   chmod 600 /certs/server.key && \
   chmod 644 /certs/server.crt /certs/root.crt"

# Run PostgreSQL with volume mount
docker run -d --name deathnode-db \
  -e POSTGRES_DB=deathnode \
  -e POSTGRES_USER=dn_admin \
  -e POSTGRES_PASSWORD=dn_pass \
  -v deathnode-db-certs:/var/lib/postgresql \
  -v $(pwd)/deathnode-database/pg_hba.conf:/var/lib/postgresql/data/pg_hba.conf \
  -p 5432:5432 \
  postgres:18 \
  -c ssl=on \
  -c ssl_cert_file=/var/lib/postgresql/server.crt \
  -c ssl_key_file=/var/lib/postgresql/server.key \
  -c ssl_ca_file=/var/lib/postgresql/root.crt
```

### 3. Apply Database Schema

```bash
psql -h localhost -U dn_admin -d deathnode -f deathnode-database/server_schema.sql
```
> Password for user dn_admin: dn_pass

### 4. Start server

```bash
cd deathnode-server/
mvn spring-boot:run
```

### 5. Run client nodeA

```bash
cd deathnode-client/
java -jar target/deathnode-client-1.0.0.jar "nodeA" "AlphaNode"
```

### 6. Run client nodeB

```bash
cd deathnode-client/
java -jar target/deathnode-client-1.0.0.jar "nodeB" "BetaNode"
```

### 7. Run monitor
```bash
cd deathnode-monitor/
sudo python3 monitor.py
```
---

## How to run from scratch

### 2. Generate Keys for Nodes

Use the new `keygen-nodes.sh` script to generate keys for one or more nodes:

```bash

cd deathnode-network/
   ./generate-all-keys.sh ca
   ./generate-all-keys.sh database
   ./generate-all-keys.sh server
   ./generate-all-keys.sh client nodeA
   ./generate-all-keys.sh client nodeB
```

**What this script does:**
- Generates Ed25519 key pairs (for signing)
- Generates RSA key pairs (for encryption)
- Creates Java Keystores (JKS) with private keys
- Stores public keys in `public_keys/` directory
- Stores private keys and keystores in `data/<node-id>/keys/`

**Generated structure:**
```
data/
├── nodeA/
│   ├── keys/
│   │   ├── keystore.jks
│   │   ├── nodeA_ed25519_priv.der
│   │   ├── nodeA_ed25519_pub.pem
│   │   ├── nodeA_rsa_priv.der
│   │   └── nodeA_rsa_pub.pem
│   ├── envelopes/
│   └── client.db (created on first run)
└── nodeB/
    └── ...
```

### 3. Update Client Database Schema

[client_schema.sql](./client_schema.sql) includes both DDL and DML statements to set up the database schema and insert public keys for the generated nodes.
Go to [../deathnode-network/deathnode-client/client-data/<node_id>/keys](../deathnode-network/deathnode-client/client-data/) and copy the Ed25519 and RSA public keys of each node into the appropriate `INSERT` statements in `client_schema.sql`, also inserting each node ID.
Go to [../deathnode-network/deathnode-server/server-data/keys/](../deathnode-network/deathnode-server/server-data/keys/) and copy the server's public keys as well.
**BE CAREFUL:** `enc_pub_key` is the RSA public key, while `sign_pub_key` is the Ed25519 public key. 

### 4. Update Server Database Schema

[client_schema.sql](./client_schema.sql) includes both DDL and DML statements to set up the database schema and insert public keys for the generated nodes.
Go to [../deathnode-network/deathnode-client/client-data/<node_id>/keys](../deathnode-network/deathnode-client/client-data/) and copy the Ed25519 and RSA public keys of each node into the appropriate `INSERT` statements in `client_schema.sql`, also inserting each node ID.
Go to [../deathnode-network/deathnode-server/server-data/keys/](../deathnode-network/deathnode-server/server-data/keys/) and copy the server's public keys as well.

**BE CAREFUL:** `enc_pub_key` is the RSA public key, while `sign_pub_key` is the Ed25519 public key. 


### Run db 

```bash
docker stop deathnode-db
docker rm deathnode-db
docker volume rm deathnode-db-certs

chmod 600 deathnode-database/keys/tls-key.pem
chmod 644 deathnode-database/keys/tls-cert.pem
chmod 644 deathnode-database/keys/ca-cert.pem

# Create a temporary container to copy files into the volume
docker volume create deathnode-db-certs
docker run --rm -v deathnode-db-certs:/certs -v $(pwd)/deathnode-database/keys:/src alpine sh -c \
   "cp /src/tls-cert.pem /certs/server.crt && \
   cp /src/tls-key.pem /certs/server.key && \
   cp /src/ca-cert.pem /certs/root.crt && \
   chown 999:999 /certs/* && \
   chmod 600 /certs/server.key && \
   chmod 644 /certs/server.crt /certs/root.crt"

# Run PostgreSQL with volume mount
docker run -d --name deathnode-db \
  -e POSTGRES_DB=deathnode \
  -e POSTGRES_USER=dn_admin \
  -e POSTGRES_PASSWORD=dn_pass \
  -v deathnode-db-certs:/var/lib/postgresql \
  -v $(pwd)/deathnode-database/pg_hba.conf:/var/lib/postgresql/data/pg_hba.conf \
  -p 5432:5432 \
  postgres:18 \
  -c ssl=on \
  -c ssl_cert_file=/var/lib/postgresql/server.crt \
  -c ssl_key_file=/var/lib/postgresql/server.key \
  -c ssl_ca_file=/var/lib/postgresql/root.crt
```

### 5. Apply Database Schema

```bash 
psql -h localhost -U dn_admin -d deathnode -f deathnode-database/server_schema.sql
```

### 1. Build the Project

```bash
cd deathnode-network/
mvn clean install
```

### 6. Start the Server

```bash
cd ../deathnode-server/
mvn spring-boot:run
```

### 7. Run a Client Node

```bash
# With default pseudonym (randomly generated)
java -jar target/deathnode-client-1.0.0.jar "nodeA" "AlphaNode"
```

### 8. Run monitor
```bash
cd deathnode-monitor/
sudo python3 monitor.py
```
---

## Configuration

All client configuration is centralized in `src/main/java/com/deathnode/client/config/Config.java`:

### Core Settings

| Setting | Purpose |
|---------|---------|
| `NODE_SELF_ID` | Unique node identifier (set via CLI argument) |
| `NODE_PSEUDONYM` | Reporter pseudonym (set via CLI, auto-generated if not provided) |
| `SERVER_HOST` | gRPC server address (default: 127.0.0.1) |
| `SERVER_PORT` | gRPC server port (default: 9090) |
| `BUFFER_THRESHOLD_TO_SYNC` | Auto-sync after N pending reports (default: 2) |

### Key & Database Paths

All paths are dynamically generated based on `NODE_SELF_ID`:

- **Database**: `data/<node-id>/client.db`
- **Envelopes**: `data/<node-id>/envelopes/`
- **Keystore**: `data/<node-id>/keys/keystore.jks`
- **Node Keys**: `data/<node-id>/keys/`
- **Public Keys**: `public_keys/`

### Keystore Details

- **Password**: `demonstration` (hardcoded for demo purposes)
- **Ed25519 Alias**: `sign-key` (signing/digital signature)
- **RSA Alias**: `rsa-key` (encryption)

---

## Advanced Usage

### Multiple Nodes on Same Machine

You can run multiple client instances on the same machine by using different node IDs:

```bash
# Terminal 1
java -jar target/deathnode-client-1.0.0.jar "nodeA" "AlphaNode"

# Terminal 2
java -jar target/deathnode-client-1.0.0.jar "nodeB" "BetaNode"
```

Each maintains separate:
- Database (`data/<node-id>/client.db`)
- Envelopes (`data/<node-id>/envelopes/`)
- Keystore (`data/<node-id>/keys/keystore.jks`)
