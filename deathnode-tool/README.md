# DeathNode Secure Document Tool

A secure document protection library implementing encryption, signing, and hash chain verification for the DeathNode scenario.

## Features

- **Confidentiality**: AES-256-GCM encryption with per-message CEK
- **Authenticity**: Ed25519 digital signatures
- **Key Wrapping**: RSA-OAEP-SHA256 for CEK distribution
- **Integrity**: GCM authentication + signature verification
- **Hash Chain**: Per-sender sequence numbers and SHA-256 hash chain for detecting missing/duplicated/out-of-order reports

## Requirements

- Java 17 or higher (for Ed25519 support)
- Maven 3.6+

## Build

Compile and generate CLI scripts:

```bash
mvn clean install
```

This will create executable scripts in `target/appassembler/bin/`:
- **Linux/Mac**: `deathnode-tool`
- **Windows**: `deathnode-tool.bat`

## Key Generation

Before using the tool, you need to generate keys for each node.

### Generate Ed25519 Keys (for signing)

```bash
# Generate Ed25519 private key
openssl genpkey -algorithm Ed25519 -out keys/nodeA_ed25519_priv.pem
openssl genpkey -algorithm Ed25519 -out keys/nodeB_ed25519_priv.pem

# Extract public key
openssl pkey -in keys/nodeA_ed25519_priv.pem -pubout -out keys/nodeA_ed25519_pub.pem
openssl pkey -in keys/nodeB_ed25519_priv.pem -pubout -out keys/nodeB_ed25519_pub.pem
```

### Generate RSA Keys (for encryption)

```bash
# Generate RSA private key (2048 bits)
openssl genrsa -out keys/nodeA_rsa_priv.pem 2048
openssl genrsa -out keys/nodeB_rsa_priv.pem 2048

# Extract public key
openssl rsa -in keys/nodeA_rsa_priv.pem -pubout -out keys/nodeA_rsa_pub.pem
openssl rsa -in keys/nodeB_rsa_priv.pem -pubout -out keys/nodeB_rsa_pub.pem
```

### Convert to DER Format (required by Java)

The tool expects keys in DER format:

```bash
# Convert Ed25519 private key to PKCS#8 DER
openssl pkcs8 -topk8 -inform PEM -outform DER -in keys/nodeA_ed25519_priv.pem -out keys/nodeA_ed25519_priv.der -nocrypt
openssl pkcs8 -topk8 -inform PEM -outform DER -in keys/nodeB_ed25519_priv.pem -out keys/nodeB_ed25519_priv.der -nocrypt

# Convert Ed25519 public key to X.509 DER
openssl pkey -pubin -inform PEM -outform DER -in keys/nodeA_ed25519_pub.pem -out keys/nodeA_ed25519_pub.der
openssl pkey -pubin -inform PEM -outform DER -in keys/nodeB_ed25519_pub.pem -out keys/nodeB_ed25519_pub.der

# Convert RSA private key to PKCS#8 DER
openssl pkcs8 -topk8 -inform PEM -outform DER -in keys/nodeA_rsa_priv.pem -out keys/nodeA_rsa_priv.der -nocrypt
openssl pkcs8 -topk8 -inform PEM -outform DER -in keys/nodeB_rsa_priv.pem -out keys/nodeB_rsa_priv.der -nocrypt

# Convert RSA public key to X.509 DER
openssl rsa -pubin -inform PEM -outform DER -in keys/nodeA_rsa_pub.pem -out keys/nodeA_rsa_pub.der
openssl rsa -pubin -inform PEM -outform DER -in keys/nodeB_rsa_pub.pem -out keys/nodeB_rsa_pub.der
```

## Usage

### 1. Protect (Encrypt and Sign)

Encrypt a report and sign it:

```bash
./target/appassembler/bin/deathnode-tool protect \
  --in reports/test_report.json \
  --out envelopes/test_envelope.json \
  --signer-node nodeA \
  --signer-priv keys/nodeA_ed25519_priv.der \
  --recipient-pub nodeA:keys/nodeA_rsa_pub.der \
  --recipient-pub nodeB:keys/nodeB_rsa_pub.der \
  --seq 1 \
  --prev-hash abc123
```

**Parameters:**
- `--in`: Input report JSON file
- `--out`: Output envelope JSON file
- `--signer-node`: Node ID of the signer
- `--signer-priv`: Ed25519 private key (DER format)
- `--recipient-pub`: Recipient public key (format: `nodeId:keyPath`), **can be specified multiple times for multiple recipients**
- `--seq`: Sequence number (monotonic integer)
- `--prev-hash`: Hash of previous envelope (optional, empty for first message)

### 2. Unprotect (Decrypt and Verify)

Decrypt an envelope and verify signature:

```bash
./target/appassembler/bin/deathnode-tool unprotect \
  --in envelopes/test_envelope.json \
  --out decrypted/test_report.json \
  --recipient-node nodeB \
  --recipient-priv keys/nodeB_rsa_priv.der \
  --sender-pub keys/nodeA_ed25519_pub.der
```

**Parameters:**
- `--in`: Input envelope JSON file
- `--out`: Output report JSON file
- `--recipient-node`: Node ID of the recipient (to find correct wrapped key)
- `--recipient-priv`: RSA private key (DER format)
- `--sender-pub`: Ed25519 public key of sender (DER format)

### 3. Check (Validate Structure)

Check envelope structure without decrypting:

```bash
./target/appassembler/bin/deathnode-tool check --in envelopes/test_envelope.json
```

This validates:
- Required fields presence
- Timestamp format and validity
- Sequence number
- Algorithm identifiers
- Encryption and signature metadata

## Input/Output Formats

### Report JSON (input to protect)

```json
{
  "report_id": "rep-001",
  "report_creation_timestamp": "2025-10-28T10:00:00Z",
  "reporter_pseudonym": "shadow_fox",
  "content": {
    "suspect": "john_doe",
    "description": "Alleged involvement in organized crime",
    "location": "Tokyo, Japan"
  },
  "version": 1,
  "status": "pending_validation"
}
```

### Envelope JSON (output from protect)

```json
{
  "metadata": {
    "report_id": "rep-001",
    "metadata_timestamp": "2025-12-07T14:30:00Z",
    "report_creation_timestamp": "2025-10-28T10:00:00Z",
    "node_sequence_number": 1,
    "prev_envelope_hash": "abc123",
    "signer": {
      "node_id": "nodeA",
      "alg": "Ed25519"
    }
  },
  "key_encrypted": {
    "encryption_algorithm": "RSA-OAEP-SHA256",
    "key_per_node": [
      {"node": "nodeA", "encrypted_key": "base64url..."},
      {"node": "nodeB", "encrypted_key": "base64url..."}
    ]
  },
  "report_encrypted": {
    "encryption_algorithm": "AES-256-GCM",
    "nonce": "base64url...",
    "ciphertext": "base64url...",
    "tag": "base64url..."
  }
}
```

## Security Properties

- **SR1 - Confidentiality**: Only authorized nodes (with corresponding RSA private keys) can decrypt reports
- **SR2 - Integrity**: GCM authentication ensures ciphertext and metadata haven't been tampered with
- **SR3 - Authenticity**: Ed25519 signatures prove who created the report
- **SR4 - Non-repudiation**: Signature binds signer to report content
- **SR5 - Ordering**: Sequence numbers and hash chain detect missing/duplicate/out-of-order reports

## Hash Chain Verification

Each envelope includes:
- `node_sequence_number`: Monotonically increasing per sender
- `prev_envelope_hash`: SHA-256 hash of previous envelope from same sender

Nodes should verify:
1. Sequence increases by 1
2. Hash of previous envelope matches `prev_envelope_hash`
3. No gaps or forks in the chain

## Architecture

```
Report (plaintext)
    |
┌───────────────────────────────┐
│ 1. Sign with Ed25519          │
│    Sign(report || metadata)   │
└───────────────────────────────┘
    |
┌───────────────────────────────┐
│ 2. Create inner payload       │
│    {report, signature}        │
└───────────────────────────────┘
    |
┌───────────────────────────────┐
│ 3. Encrypt with AES-GCM       │
│    AAD = metadata             │
└───────────────────────────────┘
    |
┌───────────────────────────────┐
│ 4. Wrap CEK with RSA-OAEP     │
│    For each recipient         │
└───────────────────────────────┘
    |
Envelope (ready to send)
```

## Testing

Create a test report:

```json
{
  "report_id": "test-001",
  "report_creation_timestamp": "2025-12-07T10:00:00Z",
  "reporter_pseudonym": "test_user",
  "content": {
    "suspect": "suspect_name",
    "description": "Test description",
    "location": "Test location"
  },
  "version": 1,
  "status": "pending_validation"
}
```

Run the complete workflow:

```bash
# 1. Protect
./target/appassembler/bin/deathnode-tool protect \
  --in reports/test_report.json --out envelopes/test_envelope.json \
  --signer-node nodeA --signer-priv keys/nodeA_ed25519_priv.der \
  --recipient-pub nodeA:keys/nodeA_rsa_pub.der \
  --recipient-pub nodeB:keys/nodeB_rsa_pub.der \
  --seq 1

# 2. Check
./target/appassembler/bin/deathnode-tool check --in envelopes/test_envelope.json

# 3. Unprotect (as nodeB)
./target/appassembler/bin/deathnode-tool unprotect \
  --in envelopes/test_envelope.json --out decrypted/decrypted_report.json \
  --recipient-node nodeB --recipient-priv keys/nodeB_rsa_priv.der \
  --sender-pub keys/nodeA_ed25519_pub.der

# 4. Verify output matches input (if diff is empty, they match)
diff reports/test_report.json decrypted/decrypted_report.json
```

## Troubleshooting

### "Ed25519 not available"
- Ensure you're using Java 17+ which includes native Ed25519 support
- Alternatively, add Bouncy Castle provider to your classpath

### "Invalid key format"
- Verify keys are in DER format (not PEM)
- Use the conversion commands above

### "GCM authentication failed"
- Envelope was tampered with
- Wrong recipient private key
- Corrupted ciphertext

### "Ed25519 signature verification failed"
- Report or metadata was modified
- Wrong sender public key
- Signature corruption