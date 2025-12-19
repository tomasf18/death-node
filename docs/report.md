# CXX DeathNode / ChainOfProduct / CivicEcho Project Report

## 1. Introduction

The project scenario that was assigned to us was DeathNode. Later on, we chose Challenge B as the security challenge to tackle.    

The corresponding description says that that DeathNode operates as a peer-backed system, however, when we discussed some implementation details with teacher **Vaibhav Arora**, he informed us that clients do not communicate with each other, as all the network reports synchronization happens in a centralized way with a special network node: the server. So the only characteristic that remains from a peer-backed system is that clients keep local copies of the network documents, eventually synchronizing with the server for consistency.  

Another important detail in the description is that all the reports are anonymous, and the system must ensure username anonimity of participants and resist attacks attempting to deanonymize or block users. We interpreted this requirement as that it should be impossible for an attacker to link a report to a specific identity, which is already satisfied *a priori* by the fact that the network does not require or store any user authentication information from nodes publications. The closest thing is a pseudonym that clients can set for themselves, but this is not linked to any real identity, as anyone can choose any pseudonym they want or even change it at any time.   

The remaining description of the project scenario is clear, so we did not have any furhter divergences in the interpretation. 

**Note**: We are not including here the remaining interpretaion as it would be redundant with the scenario description.


## 2. Goals 

Having the above interpretation of the project scenario clear and the scenario description in mind, we can now define what we want to protect and for what purpose.

We know that reports exchanged through DeathNode contain potentially sensitive information and must be protected to maintain participant anonymity and platform integrity. 
As stated in [1. Introduction](#1-introduction), participant anonymity is already satisfied.  

So the main goals of our project are:
- Confidentiality: Ensure that reports are only accessible to authorized participants during transmission and storage, so that sensitive information is not disclosed to unauthorized entities outside the anonymous network.
- Integrity: Ensure that reports are not altered or tampered with during transmission and storage (i.e., in transit and at rest), so that the information remains accurate and trustworthy, avoiding, for example, data corruption, malicious content injection from attackers that compromise the system nodes machines.
- Consistency: Ensure that all participants have a consistent view of the reports, detecting any missing, duplicated, or out-of-order reports during synchronization.  
- Authenticity: Ensure that reports are genuine and originate from legitimate participants of the network, detecting any forged or diverging histories during synchronization.

And finally, related to the challenge approach:

- Availability: Ensure that the system remains operational and accessible to legitimate participants, even in the presence of flooding attacks from misbehaving members, so that the network can continue to function effectively without disruption.


## 3. Assumptions

The DeathNode network follows a centralized synchronization model in which all nodes push reports to, and pull reports from, a central server. This server is responsible for coordinating synchronization but **is not trusted** with access to report contents: it must never be able to read plaintext data. All report contents remain encrypted **end-to-end** between authorized nodes.

The set of nodes in the anonimity network is fixed and static. The network is alredy complete at deployment time. For our demonstration, we will consider a small network of 2 clients nodes, 1 server node and the database-server, where all clients and server know each other from the begining. Being the network static, all authorized nodes are known in advance, and no dynamic membership to the network is assumed. Cryptographic keys are pre-distributed during setup, where certificates are provided through a certification authority. Every node belonging to this initial trusted set is authorized to read the reports. Nodes that are not part of this predefined group are considered unauthorized. 

Reports are immutable: once published, they cannot be edited or deleted. Anonymity is based solely on the use of pseudonyms and is therefore treated as an application-level concern rather than a cryptographic one. 

We assume no crashes or benign failures in the system. Any detected inconsistency is treated as a potential attack and leads to immediate rejection of the affected data. Under this model, availability is sacrificed in favor of strong integrity guarantees.  

Regarding the challenge, the description states "a vigilant server, appointed by the leadership". As the implementation requires 2 VM servers: a central server for synchronization and the monitor server, we assume that the monitor server is always the same, thus not necessarily "appointed by the leadership".

The overall design explicitly separates three security goals: confidentiality of report content, integrity of individual reports, and global consistency of histories across nodes. Confidentiality is provided exclusively through encryption, while integrity and ordering are enforced through per-sender chaining and compact, signed cryptographic commitments. The implementation is intentionally lightweight and strictly focused on what is required to meet the project’s security requirements, avoiding unnecessary complexity or overengineering, as also recommended by the TA's.


### Threat Model

**(_Define who is fully trusted, partially trusted, or untrusted._)
(_Define how powerful the attacker is, with capabilities and limitations, i.e., what can he do and what he cannot do_)**

In this project, we consider the following trust levels.

#### Fully Trusted 

- The Certification Authority (CA) that issues and manages cryptographic keys and certificates for network nodes. 

#### Partially Trusted

- Each Client Node: While client nodes are trusted to decrypt and read the reports they are authorized to access, they are not trusted with the integrity of the overall system. An attacker compromising a client node could attempt to tamper with report contents at rest (both in the local database or in the filesystem), inject forged reports, or withhold legitimate reports. However, the attacker cannot break cryptographic primitives or forge valid signatures without access to the node's private keys. Our solution mitigates these risks through integrity verification and consistency checks.

- The Monitor Server: The monitor server is trusted to observe network activity and detect flooding attacks from misbehaving nodes. However, it is not trusted with access to report contents and is a potential attack target. A compromised monitor server could attempt to tamper with metadata or drop legitimate reports. It cannot forge cryptographic commitments or signatures that nodes verify independently.


#### Untrusted

- The Network: The communication network is not trusted. It is assumed that an attacker could eavesdrop, intercept, modify, or inject messages during transmission between nodes. So all communications must be secured to prevent such attacks.

- The Central Server: The server is not trusted with access to report contents. It is assumed that the server could be compromised by an attacker who may attempt to read, modify, or delete reports during transmission or storage. 

- The Database Server: The database server is not trusted with access to report contents. It is assumed that the database server could be compromised by an attacker who may attempt to read, modify, or delete reports during storage. Corresponding measures are taken to handle this risk.


## 4. Solution Brief

**(_Brief solution description_)**
 
## 5. Project Development

In this section, we describe the design and implementation of our solution to protect the DeathNode network reports.

### 5.1 Secure Document Format (Security Library)

This section describes the design rationale and implementation details of the secure document library we developed to protect DeathNode reports. The library exposes three operations (`protect`, `check`, `unprotect`) and implements confidentiality, integrity, authenticity and lightweight consistency guarantees required bythe four security requirements.

#### 5.1.1 Solution Design

##### Goals

* **Confidentiality (SR1):** Only authorized nodes can read report contents. Achieved by encrypting the inner payload with a fresh symmetric Data Encryption Key (DEK) per-report and wrapping that DEK with each recipient's public key.
* **Integrity & Authenticity (SR2):** Report contents must be tamper-evident and attributable to a signer. Achieved with an Ed25519 digital signature over the canonicalized `(report || metadata)` pair.
* **Ordering / Missing / Duplicates / Fork detection (SR3, SR4):** Each sender (or client/node) maintains a per-sender monotonic `node_sequence_number` and `prev_envelope_hash` forming a lightweight per-sender hash chain, where peers validate sequence/prev-hash to detect missing, duplicate or out-of-order messages and to detect equivocation. This is used along with Merkle Roots computation to ensure global consistency during synchronization (described in section [5.2](#52-security-protocol-description-synchronization-integrity-and-consistency)).
* **Performance and security of tools:** Use of well-supported, standard algorithms available in Java’s crypto stack (AES-GCM, RSA-OAEP, Ed25519) to avoid implementing low-level crypto, as required.

##### High-level message structure (envelope)

An envelope contains three top-level sections:

1. `metadata` - descriptive fields and report history state (report_id, timestamps, node_sequence_number, prev_envelope_hash, signer id/alg).
2. `key_encrypted` - DEK wrapped for each recipient (`RSA-OAEP-SHA256`), where metadata describes the wrapping algorithm.
3. `report_encrypted` - AEAD (Authenticated Encryption with Associated Data) encrypted content performed with AES-256-GCM. Here, the metadata is bound as AAD (Associated Authenticated Data) to the AEAD operation. More details on this next.

```json  
{
  "metadata": {
    "report_id": "abc123",
    "metadata_timestamp": "2025-10-28T12:00:00Z",
    "report_creation_timestamp": "2025-10-28T10:00:00Z",
    "node_sequence_number": 42,
    "prev_envelope_hash": "hex(SHA256(previous_envelope))",
    "signer": { "node_id": "nodeA", "alg": "Ed25519" }
  },

  "key_encrypted": {
    "encryption_algorithm": "RSA-OAEP-SHA256",
    "key_per_node": [
        {"node": "self", "encrypted_key": "b64url(...)"},
        {"node": "nodeB", "encrypted_key": "b64url(...)"},
        {"node": "nodeC", "encrypted_key": "b64url(...)"},
    ]
  },

  "report_encrypted": {
    "encryption_algorithm": "AES-256-GCM",
    "nonce": "b64url(encNonce)",
    "ciphertext": "b64url(...)",
    "tag": "b64url(...)"
  }
}
```

*Inner plaintext (encrypted with DEK)* contains:

* the `report` JSON object,
* the `signature` (Ed25519 over `canonical(report) || canonical(metadata)`).

```json
{
  "report": {
    "report_id": "abc123",
    "report_creation_timestamp": "2025-10-28T12:00:00Z",
    "reporter_pseudonym": "shadow_fox",
    "content": {
      "suspect": "john_doe",
      "description": "Alleged involvement in organized crime",
      "location": "Tokyo, Japan"
    },
    "version": 1,
    "status": "pending_validation"
  },
  "signature": "b64url(Ed25519(SignPayload))"
}
```

##### Canonicalization and signing

Signatures and hash chains require a deterministic byte representation. We canonicalize JSON before hashing/signing: keys are deterministically ordered and objects serialized to UTF-8. The signature input is the concatenation of canonical `report` bytes and canonical `metadata` bytes.

> Rationale: Without deterministic canonicalization two nodes serializing the same logical JSON may produce different bytes and signatures/hashes will fail to verify.

##### Algorithms, parameters and choices

* **Symmetric encryption (AEAD):** AES-256-GCM (12-byte nonce, 128-bit tag). Metadata is used as Associated Authenticated Data (AAD) to bind metadata to content integrity.
* **Key wrapping:** RSA-OAEP with SHA-256 and MGF1 (`"RSA/ECB/OAEPWithSHA-256AndMGF1Padding"`) to encrypt per-recipient DEKs.
* **Signatures:** Ed25519 for digital signatures (signing `(report || metadata)`). We don't use any pre-hashing as Ed25519 hashes internally.
* **Signature bytes included inside the encrypted payload:** This prevents an on-path adversary from reading or replacing signatures, solving signature-swap/impersonation attacks. Also allows the other nodes to authenticate the submitter without decrypting, so as to reject invalid submissions early, before any costly decryption or compromising.
* **Hashing:** SHA-256 used for `prev_envelope_hash` (hex encoded).
* **Encoding:** In our current implementation hash fields are encoded as hexadecimal strings via `HashUtils.bytesToHex(...)`, while the remaining (keys, signatures) are base64url encoded.
* **Key separation:** Each client node and the server have two key pairs: an RSA key pair for confidentiality (DEK wrapping) and an Ed25519 pair for signing. 

##### Why these choices are effective

* AEAD provides confidentiality and integrity in one primitive, and AES-GCM is widely available and efficient for this **(add the source)**. Binding metadata as AAD ensures metadata or ciphertext cannot be changed without detection. This is because GCM produces an authentication tag that covers both the ciphertext and the AAD, so any modification to either will result in decryption failure as the tag will not match.
* RSA-OAEP with SHA-256 is a efficient way to wrap symmetric keys for a set of recipients, as RSA is performant for small data like keys. **(add the source)**
* The ideal GCM tag size is typically 128 bits (16 bytes) for strong security and broad compatibility **(add the source)**.
GCM runs CTR internally which requires a 16-byte counter. The IV provides 12 of those, the other 4 are an actual block-wise counter. If we supply a larger-than-12-bytes IV then it needs to be hashed allowing collisions to happen and raising the risk for IV reuse unneccessarily high.
* Ed25519 is used for signatures because it is fast, small, and standardized for signatures (not good, however, for encryption). This was chosen over ECDSA due to its stronger security guarantees and safer design **(add the source)**.
* Signing and encryption address different security properties. Using separate keys avoids cross-protocol attacks and follows standard cryptographic practice (e.g., TLS, PGP, Signal), ensuring that compromising one key does not compromise the other security properties, which increases overall system robustness. **(add the source)**
* The per-sender sequence number plus the prev-hash is simple and efficient to detect missing, duplicate or forked sequences in a synced peer network where a full consensus protocol would be overkill.

#### 5.1.2 Implementation & Technologies

##### API summary

* `protect(report, metadata, recipientPubKeys, signerPrivKey)` - outputs the Envelope  
  Produces an envelope with encrypted report payload and a set of wrapped DEKs for recipients.

* `check(envelope)` - outputs { valid: boolean, reasons: [String] }  
  Performs structural and semantic validation of the envelope. 

* `unprotect(envelope, recipientPrivKey, recipientNodeId, senderPubKey)` - outputs the Report  
  Unwraps DEK for `recipientNodeId`, decrypts AES-GCM payload (AAD = metadata) and verifies Ed25519 signature. Throws `SecurityException` on AEAD failure or signature failure.

##### Implementation details

* **Canonical JSON:** The code calls `canonicalJson(...)` on `JsonObject`s and then uses the resulting bytes as the canonical representation for signatures and AEAD AAD. 
* **Signing:** `signDataHex(payload, signerPrivKey)` signs `(report || metadata)` using `Signature.getInstance("Ed25519")` and produces a hex string.
* **Inner payload & encryption:** The `report` and `signature` are serialized to JSON and encrypted as the AEAD plaintext using AES-256-GCM. The metadata bytes are supplied to `Cipher.updateAAD(...)` before `doFinal(...)`.
* **DEK generation & wrapping:** A fresh AES key is generated for each envelope (`KeyGenerator.getInstance("AES"); kgen.init(256)`) and then wrapped individually for every recipient using RSA-OAEP (`Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")`).
* **Encoding:** The implementation uses hex encoding (via `HashUtils.bytesToHex`) for nonce, ciphertext, tag and wrapped keys as stored in `Envelope`. The envelope JSON contains `metadata`, `key_encrypted`, and `report_encrypted` sections.
* **check() behavior:** `check()` verifies presence and basic format rules for:

  * `metadata` fields (presence, timestamp parsing, if `signerAlg` is `Ed25519`, non-negative sequence number),
  * `key_encrypted` (algorithm string equals `"RSA-OAEP-SHA256"`, non-empty recipient key list, each entry has `node` and `encrypted_key`),
  * `report_encrypted` (algorithm `"AES-256-GCM"`, presence of `nonce`, `ciphertext`, `tag`).  
    `check()` intentionally does not perform cryptographic verification (unwrapping DEK or signature verification) because those require secret keys.    
    It is a fast structural validation intended to detect malformed or obviously invalid envelopes before attempting expensive crypto ops.


### **5.2 Security Protocol: Synchronization, Integrity and Consistency**

This section describes the synchronization protocol: how nodes submit reports, how the system synchronizes them, and how it detects missing, duplicated or out-of-order reports (SR3) and forged or diverging histories (SR4). The text explains the data that travels between parties, the commitments that nodes make, the checks performed by the server and by clients, and the response to any inconsistency. 


#### **5.2.1 Solution Design**

##### **Report submission -  local persistence and per-sender ordering**

Each report is produced by a node and information of that report is immediately stored on disk (filesystem) at that node in **encrypted form**. The file is the canonical encrypted artifact for that report and contains only ciphertext and the minimal metadata required to locate and verify it later.

In addition to the encrypted file, each node maintains an append-only sequence for the reports it publishes: every new report includes a cryptographic link to (or the hash of) the previous report published by the same node. This per-sender link provides an immutable local ordering for that publisher.

When the file is created, the node also updates the database with relevant metadata (e.g., the file path), and appends the new report to an in-memory buffer (a list of file paths). This buffer accumulates all **unsynced** reports until synchronization is triggered.


##### **Clear outer metadata and AEAD binding**

Each report contains an outer clear header that contains only **non-sensitive routing and ordering fields** (for example: a timestamp for coarse global sorting, the per-sender sequence number and the per-sender link to the previous report).

This outer metadata is kept in cleartext so the server can perform global ordering without accessing report contents. However, and as referenced in [5.1.1](#511-solution-design), the metadata is **bound to the encrypted payload** using authenticated encryption with associated data (AEAD, via AES-GCM). So, any modification to the metadata will cause decryption to fail at receiving nodes.

End-to-end authorship and integrity are guaranteed by a digital signature placed **inside** the encrypted payload (see [5.1.1](#511-solution-design)). 

##### **Merkle commitments for buffers**

When a synchronization round is initiated, each node computes a **Merkle tree** over the buffered encrypted files and derives a Merkle root. The node signs this Merkle root with its signing key and persists the signed root.

The signed root is the node’s compact, tamper-evident **commitment** to the exact set and ordering of the buffered items at that moment. This lets the node prove later which exact envelopes it intended to publish in that sync round.

##### **Synchronization procedure (message flow)**

1. Synchronization is initiated either periodically or on demand.  
2. When a node needs to sync with the server, it sends a **`Hello`** (with *`sync flag`* set) request.  
3. The server acknowledges the creation of a new sync round and requests all the contents present in all clients’ buffers (**`RequestBuffer`**).  
4. Each client then sends to the server a **`BufferUpload`** message containing:  
   - Up to **N** buffered envelopes (N is configurable),  
   - The **Merkle root** of the buffer,  
   - The node's **signature** on that Merkle root.  
5. The server receives the buffers from all nodes and **verifies each node's buffer**:
   - Verify the node's signature on the Merkle root.
   - Recompute the Merkle root from the received envelopes and ensure it matches the signed root -  this guarantees the set of files the server received is exactly the set the node committed to (prevents in-transit tampering or lost contents).
   - Verify per-sender **hash chain continuity**: ensure the first report sent by each node links correctly to the last known synced report from that node, and that all subsequent reports link correctly to their predecessors (this detects missing, duplicated or out-of-order reports per sender).  
   **Note:** The hash chain *per se* does not detect if the last envelope (in each nodes' buffer or in the `SyncResult` from the server) is missing, as this would require knowledge of future envelopes. This is detected instead by the Merkle root commitment, which binds the entire set of envelopes. So they work together to provide full integrity guarantees.
6. If all buffers are valid, the server sorts all received envelopes **globally** by their metadata timestamp, producing a single, totally ordered list of all reports from all nodes.
7. The server constructs a new **block** that commits to this global ordering:
   - Compute the Merkle root of the ordered envelopes,
   - Link to the previous block’s root,
   - Sign the new block root,
   - Increment the block number.
8. The server then broadcasts a **`SyncResult`** message to all clients containing:
   - The globally ordered list of envelopes,
   - The new block root, signed block root, block number, and previous block root,
   - The signed Merkle roots that it received from each node (for cross-verification).
9. Each client receives the `SyncResult` and performs a **multi-stage verification pipeline** (described next, in [Client verification: multi-stage pipeline](#client-verification-multi-stage-pipeline-on-receiving-syncresult)).

> Note: each step above corresponds to concrete protocol messages (`Hello`, `RequestBuffer`, `BufferUpload`, `SyncResult`, which will be referenced in [5.2.2](#522-implementation--technologies)) and concrete checks the server executes on received data.

##### **Client verification: multi-stage pipeline (on receiving `SyncResult`)**

Upon receiving a `SyncResult`, each client performs a **multi-stage verification pipeline**. Only after all six stages pass does the client accept the sync result. Any failure triggers immediate rejection and an error report to the server, followed by cleanup of unsynced data.

**Stage 1: Server Block Signature**
- Verify `signed_block_root` using the server's Ed25519 public key.  
- **Protects against:** Server impersonation, forged sync results.

**Stage 2: Block Chain Continuity**
- Compare the `block number` and `previous block root` with locally stored last block.  
- Ensure `block number = last block number + 1`.  
- Ensure `previous block root` matches last known `block root`.  
- **Protects against:** Server equivocation, forked histories (SR4).

**Stage 3: Block Merkle Root**
- Recompute Merkle root from received ordered envelopes.  
- Verify it matches `block root`.  
- **Protects against:** Server reordering, omitting, or modifying envelopes (SR3, SR4).

**Stage 4: Per-Node Buffer Signatures**
- For each node that participated in the round:
  - Verify the signature on the node's `signed buffer root` using that node's public key.  
- **Protects against:** Server fabricating buffers on behalf of nodes (SR4).

**Stage 5: Per-Sender Hash Chains**
- For each sender (nodeA, nodeB, etc.), extract their envelopes from the ordered list and verify:
  - The first envelope's `previous envelope hash` matches the last known synced envelope hash for that sender,
  - Each subsequent envelope links correctly to its predecessor,
  - Sequence numbers increment by 1.  
- **Protects against:** Missing, duplicated or reordered reports per sender (SR2, SR3).

**Stage 6: Individual Envelope Processing**
- For each envelope:
  - Store to disk with deterministic filename (hash-based),
  - Decrypt and verify signature,
  - Update local database.
- **Protects against:** Corrupted files, forged reports (SR1, SR2).

Only if all verification stages pass does the client accept the sync result, store the new envelopes, and update its local state. Any failure triggers immediate rejection, error reporting to the server.


##### **Merkle Tree Commitments (SR3)**

Why this is used (SR3):
- **Batch integrity:** The Merkle root is a compact (32-byte) cryptographic commitment to the **exact set and order** of envelopes in the buffer.
- **Tamper detection:** If an attacker modifies, adds, or removes even one envelope in transit, the recomputed Merkle root will differ from the signed root.
- **Non-repudiation:** Because the sender signed the root before transmission, they cannot later deny having sent that specific set of envelopes.

This technique efficiently detects **missing or tampered reports at the batch level** before individual processing begins.

This creates a **server-maintained blockchain** where each block:
- Commits to a specific global ordering of reports,
- References the previous block cryptographically,
- Is signed by the server.

How this satisfies SR4 (Consistency):
- **Fork detection:** If the server attempts to produce two different orderings for the same sync round (equivocation), clients will detect mismatching block roots.
- **History continuity:** The `previous block root` chain ensures that past synchronization rounds cannot be retroactively altered without breaking the chain.
- **Cross-node verification:** Clients verify that other nodes' signed Merkle roots match what the server claims to have received, detecting server-side tampering with buffer contents.


##### **Data-at-Rest Integrity Verification (SR2, SR4)**

If a client’s local storage is compromised (for example, encrypted report files are altered directly on disk), the system must be able to detect such tampering.   

Because continuously re-verifying all stored reports would be computationally expensive, these checks are performed **on-demand**, whenever a report is accessed or audited. Each client can verify that locally stored data at rest has not been modified. The `list-reports` command performs integrity checks before decrypting and listing reports. Any tampering is detected by the `unprotect()` tool: AES-GCM authentication will fail if ciphertext or AAD were changed, while the Ed25519 signature verification will fail if plaintext was altered. And this error is immediately reported to the user.

This provides defense against:
  - Filesystem corruption,
  - Malicious local process modifications,
  - Storage media bit-flips.


##### **Security Properties Summary**

| Requirement | Mechanism | Enforcement Point |
|------------|-----------|------------------|
| **SR1: Confidentiality** | End-to-end AES-GCM encryption | Only authorized nodes can decrypt |
| **SR2: Integrity (individual)** | End-to-end AES-GCM and Ed25519 signatures inside ciphertext | Client verification upon decryption |
| **SR3: Integrity (batch)** | Per-sender hash chains + Merkle trees | Server and client verification |
| **SR4: Consistency** | Signed block roots + block chaining | Client multi-stage verification pipeline |


#### **5.2.2 Implementation & Technologies**

##### **Protocol Definition**

The synchronization protocol is defined using **Protocol Buffers 3** (`.proto` files).

Key message types:
- `Hello`: Connection establishment and sync initiation
- `BufferUpload`: Client submits envelopes + signed Merkle root
- `RequestBuffer`: Server requests buffers from all clients
- `SyncResult`: Server distributes globally ordered envelopes + block commitment
- `SignedBufferRoot`: Per-node buffer commitment metadata
- `Error`: Error reporting between parties

Communication uses **gRPC bidirectional streaming** over HTTP/2.

##### **Client Implementation**

Main technology stack:
- Java 21
- SQLite 3 - Lightweight, file-based relational database
- gRPC-Java - High-performance, open-source RPC framework that allows clients and servers to communicate transparently
- Gson - JSON parsing for envelope processing

##### **Server Implementation**

Main technology stack:
- Java 21
- Spring Boot 3 - Application framework with dependency injection, transaction management
- PostgreSQL 18 - Relational database for persistent storage
- JPA/Hibernate - ORM for database access
- gRPC Spring Boot Starter - Integrates gRPC server with Spring lifecycle
- Gson

##### **Cryptographic Operations**

Merkle tree implementation:
- Binary tree construction using SHA-256
- Leaf nodes: `H(envelope_bytes)`
- Internal nodes: `H(left_child || right_child)`
- Padding: If odd number of leaves, duplicate last leaf
- Root: 32-byte hash commitment

Hash operations:
- Algorithm: SHA-256
- Output: 32 bytes (256 bits)
- Used for: Envelope hashes, Merkle tree nodes, block roots, hash chains
- Library: `java.security.MessageDigest`


### 5.3 Security Challenge

The fact that the reports are anonymous, could easily lead to attacks where users flood the network with fake reports, trying to harden the system and make it unusable for legitimate users. With this in mind, we decided that Challenge B would be the most interesting one to tackle, as it requires us to design a solution that resists such attacks. 

#### 5.3.1 Solution Design

**(_Describe the new requirements introduced in the security challenge and how they impacted your original design._)
(_Explain how your team redesigned or extended the solution to meet the security challenge, including key distribution and other security measures._)**

This security challenge required us to introduce a new entity in the system: the monitor server. This server is responsible for observing network activity and detecting flooding attacks from misbehaving nodes. As it is possible to observe in figure **[network-architecture-diagram]**, we leveraged the fact that we needed a gateway from each client network to the central server, and vice versa, to place the monitor server in between them. This way, all client-server communications pass through the monitor, allowing it to effectively monitor traffic and identify potential flooding attacks.

We also leveraged the fact that each client keeps an in memory strucure (buffer, or more simple, a list with the unsynced reports), and that it uses it to decide when to send Sync requests to the server (fixed-size messages), when the buffer reaches a certain size. This means that a misbehaving client that wants to flood the network with fake reports, would have to send a lot of Sync requests to the server in a short period of time, as each Sync request can only carry a limited number of reports.

#### 5.3.2 Implementation & Technologies

This monitor server was implemented using **Python**.
It listens for incoming connections from client nodes to the central server, using the packet size to detect Sync requests. With this detection, it is able to count how many Sync requests each client sends in a given time window (e.g., per minute). If a client exceeds a predefined threshold of Sync requests within that time window, the monitor server flags the client as misbehaving and blocks its further communications to the central server. This is implemented by simply dropping any packets from the misbehaving client, thus not forwarding them to the central server. This block is not permanent, as after a certain timeout period, the monitor server will unblock the client and allow it to communicate with the central server again.

As the monitor server is only able to detect that a client is flooding the network with when the first Sync that exceeds the threshold is received, it allways blocks the client from that point on. Thus, when the server then request all the buffers from all connected clients, it is configured to timeout when a client is taking too long to respond, and then just ignores that client for that synchronization round. On the client side, when responding with the requested buffer, after the block, this message is dropped and the client does not receive any response from the server, so it is also configured to timeout after a certain period of time waiting for the server response. If this timeout is reached, the client is assumed compromised and drops all the reports in its buffer, rolling back its state to the last successful synchronization.

### 5.4 Architecture

(_Include a structural diagram, in UML or other standard notation._)
(_Include a architecture diagram)

### 5.5 Infrastructure


#### 5.5.1 Network and Machine Setup

6.2. Firewall rules
6.3. Justification of the chosen infrastructure

(_Provide a brief description of the built infrastructure._)

(_Justify the choice of technologies for each server._)

#### 5.5.2 Communication Security

(_Discuss how server communications were secured, including the secure channel solutions implemented and any challenges encountered._)

(_Explain what keys exist at the start and how are they distributed?_)

## 6. Conclusion

(_State the main achievements of your work._)

(_Describe which requirements were satisfied, partially satisfied, or not satisfied; with a brief justification for each one._)

(_Identify possible enhancements in the future._)
Hash mercke root with previous merkle root
Error handling
When detected local tampering, oprion to remove corrupted files

(_Offer a concluding statement, emphasizing the value of the project experience._)

## 7. Bibliography

(_Present bibliographic references, with clickable links. Always include at least the authors, title, "where published", and year._)

----
END OF REPORT
