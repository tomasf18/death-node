# DeathNode Client - Quickstart Guide

This document shows how to fully initialize and run a **DeathNode client** from a clean machine:

- Data directories  
- Keystore (JKS) with **private keys only**  
- SQLite database with **public keys only**  
- Building and launching the client  
- Creating and verifying reports  

This guide uses two test nodes:

- **nodeA** -> self node (the machine running the client)
- **nodeB** -> remote peer node

---

# 1. Clean Start (Hard Reset Environment)

From the project root:

```bash
cd deathnode-client/
rm -f data/client.db
rm -rf data/envelopes/
rm -f keys/keystore.jks
mkdir -p data/envelopes
```

---

# 2. Generate Cryptographic Keys

You need **two key pairs** for your node:

| Purpose           | Algorithm | Where Stored               |
| ----------------- | --------- | -------------------------- |
| Encryption        | RSA       | JKS (private), DB (public) |
| Digital Signature | Ed25519   | JKS (private), DB (public) |

---

## 2.1 Generate Ed25519 Signing Key

```bash
openssl genpkey -algorithm Ed25519 -out keys/nodeA_ed25519_priv.pem
openssl pkey -in keys/nodeA_ed25519_priv.pem -pubout -out keys/nodeA_ed25519_pub.pem

openssl genpkey -algorithm Ed25519 -out keys/nodeB_ed25519_priv.pem
openssl pkey -in keys/nodeB_ed25519_priv.pem -pubout -out keys/nodeB_ed25519_pub.pem
```

---

## 2.2 Generate RSA Encryption Key

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/nodeA_rsa_priv.pem
openssl pkey -in keys/nodeA_rsa_priv.pem -pubout -out keys/nodeA_rsa_pub.pem

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out keys/nodeB_rsa_priv.pem
openssl pkey -in keys/nodeB_rsa_priv.pem -pubout -out keys/nodeB_rsa_pub.pem
```

---

# 3. Create the Java Keystore (JKS)

The keystore stores **PRIVATE KEYS ONLY**, but PKCS12 import **requires a certificate** for each private key.

---

### 3.1 Create Empty Keystore

```bash
keytool -genkeypair \
  -alias temp \
  -keystore keys/keystore.jks \
  -storepass demonstration \
  -keyalg RSA \
  -keysize 2048 \
  -dname "CN=temp" \
  -validity 365
```

---

### 3.2 Generate Self-Signed Certificates for Node's Keys

```bash
# RSA certificate
openssl req -new -x509 \
  -key keys/nodeA_rsa_priv.pem \
  -out keys/nodeA_rsa_cert.pem \
  -days 365 \
  -subj "/CN=A.A.A.A"

# Ed25519 certificate
openssl req -new -x509 \
  -key keys/nodeA_ed25519_priv.pem \
  -out keys/nodeA_ed25519_cert.pem \
  -days 365 \
  -subj "/CN=A.A.A.A"
```

---

### 3.3 Convert Node's Private Keys + Certificates to PKCS12

```bash
# RSA
openssl pkcs12 -export \
  -inkey keys/nodeA_rsa_priv.pem \
  -in keys/nodeA_rsa_cert.pem \
  -name rsa-key \
  -out keys/nodeA_rsa.p12 \
  -passout pass:demonstration

# Ed25519
openssl pkcs12 -export \
  -inkey keys/nodeA_ed25519_priv.pem \
  -in keys/nodeA_ed25519_cert.pem \
  -name sign-key \
  -out keys/nodeA_ed25519.p12 \
  -passout pass:demonstration
```

---

### 3.4 Import PKCS12 Files into the JKS

```bash
# RSA key
keytool -importkeystore \
  -srckeystore keys/nodeA_rsa.p12 \
  -srcstoretype PKCS12 \
  -srcstorepass demonstration \
  -destkeystore keys/keystore.jks \
  -deststoretype JKS \
  -deststorepass demonstration \
  -alias rsa-key

# Ed25519 key
keytool -importkeystore \
  -srckeystore keys/nodeA_ed25519.p12 \
  -srcstoretype PKCS12 \
  -srcstorepass demonstration \
  -destkeystore keys/keystore.jks \
  -deststoretype JKS \
  -deststorepass demonstration \
  -alias sign-key
```

---

### 3.5 Verify Keystore

```bash
keytool -list -keystore keys/keystore.jks -storepass demonstration
```

Expected:

```
rsa-key
sign-key
```

---

# 4. Edit DB Schema File to Insert Public Keys into Database

---

## 4.1 Insert nodeA (Self Node)

```sql
INSERT OR REPLACE INTO nodes(node_id, enc_pub_key, sign_pub_key)
VALUES (
  'A.A.A.A',
  '-----BEGIN PUBLIC KEY-----
<PASTE nodeA_rsa_pub.pem CONTENT HERE>
-----END PUBLIC KEY-----',
  '-----BEGIN PUBLIC KEY-----
<PASTE nodeA_ed25519_pub.pem CONTENT HERE>
-----END PUBLIC KEY-----'
);
```

---

## 4.2 Insert nodeB (Remote Node)

```sql
INSERT OR REPLACE INTO nodes(node_id, enc_pub_key, sign_pub_key)
VALUES (
  'B.B.B.B',
  '-----BEGIN PUBLIC KEY-----
<PASTE nodeB_rsa_pub.pem CONTENT HERE>
-----END PUBLIC KEY-----',
  '-----BEGIN PUBLIC KEY-----
<PASTE nodeB_ed25519_pub.pem CONTENT HERE>
-----END PUBLIC KEY-----'
);
```

---

# 5. Initialize SQLite Database

Create DB:

```bash
sqlite3 data/client.db < client_schema.sql
```

Verify tables:

```bash
sqlite3 data/client.db ".tables"
```

Expected:

```
nodes
nodes_state
reports
block_state
```

---

# 6. Build the Client

```bash
mvn clean package
```

---

# 7. Launch Client

```bash
java -jar target/client.jar
```

Expected:

```
DeathNode client started.
Type 'help' to see available commands.
deathnode-client>
```

---

# 8. Create a Report (Primary Test)

```bash
deathnode-client> create-report
```

You will be prompted:

```
Suspect:
Description:
Location:
```

Expected output:

```
Created envelope: <SHA256_HASH>.json
```

---

# 9. Verify Filesystem Output

```bash
ls data/envelopes/
```

You must see:

```
<hash>.json
```

Inspect contents:

```bash
cat data/envelopes/<hash>.json | jq .
```

You must see:

* Metadata
* Encrypted CEK map
* Ciphertext
* Digital signature

---

# 10. Verify Database Writes

```bash
sqlite3 data/client.db
.headers on
.mode column
```

### Check reports table:

```sql
SELECT envelope_hash,
       signer_node_id,
       node_sequence_number,
       prev_envelope_hash,
       file_path
FROM reports;
```

Expected:

| envelope_hash | signer_node_id | node_sequence_number | prev_envelope_hash | file_path                    |
| ------------- | -------------- | -------------------- | ------------------ | ---------------------------- |
| `<hash>`      | `nodeA`        | `1`                  | empty              | `data/envelopes/<hash>.json` |

---

### Check node state:

```sql
SELECT * FROM nodes_state;
```

Expected:

| node_id | last_sequence_number | last_envelope_hash |
| ------- | -------------------- | ------------------ |
| nodeA   | 1                    | `<hash>`           |

---

# 11. Create a Second Report (Chain Validation)

```bash
deathnode-client> create-report
```

Then:

```sql
SELECT node_sequence_number, prev_envelope_hash
FROM reports
ORDER BY node_sequence_number;
```

Expected:

| seq | prev_envelope_hash |
| --- | ------------------ |
| 1   | empty              |
| 2   | `<hash-of-1>`      |


---

# 12. List Reports Command

```bash
deathnode-client> list-reports
```
