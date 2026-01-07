## Timeline breakdown

### Note:
- Leave server already working

### 1. Initialization, report creation and listing

* Show server and both nodes establishing connections.
* Emphasize that all communication occurs over authenticated secure channels (logs show that).

**Actions:**

1. On Node A: create 1 report
2. On Node B: create 1 report
4. List reports on both nodes (including unprotect steps)

**What to show:**

* Reports are created locally and stored encrypted.
* No synchronization has occurred yet.
* Listing shows **unsynced reports** still buffered.

---

### 2. Synchronization and normal operation 

**Actions:**

1. On Node A: trigger synchronization
   *(note that this can be manual or automatic periodically)*
2. On Node B: list reports

**What to show:**

* On both nodes:
  * buffer request from server,
  * buffer upload to server,
  * reception of `SyncResult`,
  * full verification pipeline,
  * complete list of network reports (with global seq num updated)
* On the server:
  * sync request handling, broadcasting buffer upload requests to nodes,
  * buffer collection,
  * full verification pipeline,
  * block creation and broadcast.

#### ----------------- 35s -----------------

### 3. Tampering detection (AEAD)

**Actions:**

1. On Node B: create one report
2. Manually modify one byte in the encrypted envelope on disk on the **metadata** field (e.g., change a letter on report ID)
3. Trigger synchronization from Node B
4. On Node A: list reports

**What to show:**

* The server accepts the envelope (it does not read contents).
* Node A detects tampering **during decryption / verification**.
* Demonstrates end-to-end integrity enforcement (SR2).

---

### 4. Monitor enforcement (Challenge B)

**Actions:**

1. On Node B: repeatedly:

   * create a report,
   * trigger synchronization,
     until blocked by the monitor
2. Trigger one additional sync to show increased penalty
3. On Node B: list reports to show buffer cleared
4. On Node A:

   * create reports,
   * trigger sync to show normal operation continues
5. After block timeout:

   * On Node B: create one report and sync again

**What to show:**

* Monitor detects abusive behavior and enforces blocking.
* Node B experiences temporary denial but recovers automatically.
* Honest nodes remain unaffected ans system continues to operate normally to them.
* Demonstrates availability protection under attack (Challenge B).

---

### 5. Detection of order tampering (SR3)

**Setup:**
* Restart the server using the **SR3 violation Spring profile** that simulates envelope reordering **after block creation**.
**How to run**:
  ```bash
  mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=violation-sr3"
  ```
* **Behavior**: After buffer verifications pass, envelopes are sorted by timestamp, and block merkle root is computed, the first envelope is moved to the last position. 

**Actions:**

1. On Node A: create 2 reports
2. On Node B: create 2 reports
3. Trigger synchronization

**What to show:**

* Both nodes reject the synchronization result.
* Failure occurs during verification of merkle root since the tampering was performed after block creation.
* Demonstrates detection of reordered histories (SR3) (would also work if tampering occurred before block creation, because of hash chaining).

---

### 6. Detection of diverging history (SR4)

**Setup:**
* Server remains in **violation profile**, simulating an incorrect previous hash for one sender.
**How to run**:
  ```bash
  mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=violation-sr4"
  ```
* **Behavior**: For a buffer sent to the server containing exactly 1 envelope, the server modifies the `prev_envelope_hash` field in the envelope's metadata to a fake hash value (different from the actual previous envelope hash). This breaks and diverges the per-sender envelope chain history (i.e., fork the history of the last 2 uploaded envelopes).

**Actions:**

1. On Node A: create 2 reports
2. Trigger synchronization
3. On Node A: create another report
4. Trigger synchronization again

**What to show:**

* Nodes detect a break in per-sender history continuity.
* Synchronization is rejected immediately.
* Demonstrates fork / equivocation detection (SR4). 