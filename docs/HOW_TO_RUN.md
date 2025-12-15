## How to run with current configuration

### 1. Build the Project

```bash
cd deathnode-network/
mvn clean install
```

### 2. Run the Database

```bash
docker run -d --name deathnode-db -e POSTGRES_DB=deathnode -e POSTGRES_USER=dn_admin -e POSTGRES_PASSWORD=dn_pass -p 5432:5432 postgres:18
```

### 3. Apply Database Schema

```bash
psql -h localhost -U dn_admin -d deathnode -f deathnode-database/server_schema.sql

Password for user dn_admin: dn_pass
```

### 4. Start server

```bash
cd deathnode-server/
mvn spring-boot:run
```

### 5. Run client nodeA

```bash
java -jar deathnode-client/target/deathnode-client-1.0.0.jar "nodeA" "AlphaNode"
```

### 6. Run client nodeB

```bash
java -jar deathnode-client/target/deathnode-client-1.0.0.jar "nodeB" "BetaNode"
```
---

## How to run from scratch

### 1. Build the Project

```bash
cd deathnode-network/
mvn clean install
```

### 2. Generate Keys for Nodes

Use the new `keygen-nodes.sh` script to generate keys for one or more nodes:

```bash
cd deathnode-client/

# Generate keys for a single node
./keygen-nodes.sh "nodeA"

# Or generate keys for multiple nodes at once
./keygen-nodes.sh "nodeA" "nodeB" "nodeC"
```

**What this script does:**
- Generates Ed25519 key pairs (for signing)
- Generates RSA key pairs (for encryption)
- Creates Java Keystores (JKS) with private keys
- Stores public keys in `public_keys/` directory
- Stores private keys and keystores in `data/<node-id>/keys/`

**Generated structure:**
```
public_keys/
├── nodeA_ed25519_pub.pem
├── nodeA_rsa_pub.pem
├── nodeB_ed25519_pub.pem
└── nodeB_rsa_pub.pem

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
Go to [public_keys/](./public_keys/) and copy the public keys of each node into the appropriate `INSERT` statements in `client_schema.sql`, also inserting each node ID.
**BE CAREFUL:** `enc_pub_key` is the RSA public key, while `sign_pub_key` is the Ed25519 public key. 

### 4. Update Server Database Schema

[server_schema.sql](../deathnode-database/server_schema.sql) includes both DDL and DML statements to set up the server database schema and insert public keys for the generated nodes.
Please update that file according to the nodes you generated, similar to the client schema update.

### 5. Apply Database Schema

```bash 
psql -h localhost -U dn_admin -d deathnode -f deathnode-database/server_schema.sql
```

### 6. Start the Server

```bash
cd ../deathnode-server/
mvn spring-boot:run
```

### 7. Run a Client Node

```bash
# With default pseudonym (randomly generated)
java -jar target/deathnode-client-1.0.0.jar "nodeA"

# With custom pseudonym
java -jar target/deathnode-client-1.0.0.jar "nodeA" "shadow_fox"

# Run another node
java -jar target/deathnode-client-1.0.0.jar "nodeB" "night_owl"
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
java -jar target/deathnode-client-1.0.0.jar "nodeA" "node_a"

# Terminal 2
java -jar target/deathnode-client-1.0.0.jar "nodeB" "node_b"

# Terminal 3
java -jar target/deathnode-client-1.0.0.jar "nodeC" "node_c"
```

Each maintains separate:
- Database (`data/<node-id>/client.db`)
- Envelopes (`data/<node-id>/envelopes/`)
- Keystore (`data/<node-id>/keys/keystore.jks`)

### Adding New Nodes

To add a new node after initial setup:

1. Generate its keys:
   ```bash
   ./keygen-nodes.sh "4.4.4.4"
   ```

2. Run the client:
   ```bash
   java -jar target/deathnode-client-1.0.0.jar "4.4.4.4"
   ```

3. Update the database schema to add the new node's public keys (or use a management utility).

---
