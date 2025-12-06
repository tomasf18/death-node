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

# client

## Clean Start (Hard Reset Test Environment)

**Do this once before testing at the root (deathnode-client/):**

```bash
rm -f data/client.db
rm -rf data/envelopes/
mkdir data/envelopes
```

Then re-run DB init script:

```bash
sqlite3 data/client.db < client_schema.sql
```

Verify tables:

```bash
sqlite3 data/client.db ".tables"
```

You should see:

```
nodes
nodes_state
reports
block_state
```

Verify bootstrap node:

```bash
sqlite3 -header -column data/client.db "SELECT * FROM nodes;"
```

Expected:

```
1.1.1.1|00|00
```

You can test other commands:

```bash
sqlite3 -header -column data/client.db "SELECT * FROM reports;"
```
## Create jar executable

From `deathnode-client/` run:

```bash
mvn clean package
```

## Launch Client in Interactive Mode

From same dir, run your client normally:

```bash
java -jar target/client.jar 
```

You should land in something like:

```
deathnode-client>
```

---

## Test Report Creation (Primary Test)

Run:

```bash
deathnode-client> create-report
```

You should be prompted:

```
Suspect:
Description:
Location:
```

Expected terminal output:

```
Created envelope: <SHA256_HASH>.json
```

---

## Verify Filesystem Output

List envelopes:

```bash
ls data/envelopes/
```

You must see:

```
<hash>.json
```

Now inspect contents:

```bash
cat data/envelopes/<hash>.json | jq .
```

You must see:

- Envelope structure
- Dummy encryption fields
- Metadata populated correctly:

```json
{
  "metadata": {
    "report_id": "...",
    "metadata_timestamp": "...",
    "report_creation_timestamp": "...",
    "node_sequence_number": 1,
    "prev_envelope_hash": "",
    "signer": { "node_id": "1.1.1.1", "alg": "Ed25519" }
  }
}
```

- Key wrapping exists
- Ciphertext is base64
- Signature placeholder exists inside payload

---

## Verify Database Writes

Open SQLite:

```bash
sqlite3 client.db
```

For better output formatting, run:

```sql
.headers on
.mode column
```

### Check `reports`

```sql
SELECT envelope_hash,
       signer_node_id,
       node_sequence_number,
       global_sequence_number,
       prev_envelope_hash,
       file_path
FROM reports;
```

Expected:

| Field                  | Expected               |
| ---------------------- | ---------------------- |
| envelope_hash          | `<hash>`               |
| signer_node_id         | `1.1.1.1`              |
| node_sequence_number   | `1`                    |
| global_sequence_number | `NULL`                 |
| prev_envelope_hash     | empty                  |
| file_path              | `envelopes/<hash>.env` |

---

### Check `nodes_state`

```sql
SELECT * FROM nodes_state;
```

Expected:

| node_id | last_sequence_number | last_envelope_hash |
| ------- | -------------------- | ------------------ |
| 1.1.1.1 | 1                    | `<hash>`           |



## Create a Second Report (Chain Validation)

Run again:

```bash
deathnode-client> create-report
```

Fill different data.

Then re-check:

```sql
SELECT envelope_hash,
       signer_node_id,
       node_sequence_number,
       prev_envelope_hash
FROM reports ORDER BY node_sequence_number;
```

Expected:

| seq | prev_envelope_hash |
| --- | ------------------ |
| 1   | empty              |
| 2   | `<hash-of-1>`      |

Hash chaining is now correct.
