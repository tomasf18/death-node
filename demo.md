# Demo (120 seconds)

## Which terminals/windows to record

* Top-left: **Server logs** - Terminal A
* Top-right: **Monitor logs** - Terminal B
* Bottom-left: **Node A terminal** - Terminal C
* Bottom-right: **Node B terminal** - Terminal D

---

## Pre-record checklist (do this before recording)

* Increase terminal font size and set high-contrast colors. 
* Check if `list-reports` presents a clear output in those window configurations.
* Prepare the commands per component (server, monitor, Node A, Node B) so that only **ENTER** is required when recording starts.
* **Spring Boot profiles setup (critical):**
    * For violation demonstrations, the server is **stopped and restarted** with the **`violation` profile**
    * No source code is modified during recording
* Set the monitor threshold to a reproducible value (**3 syncs / 30 seconds**) so that all tests below work correctly.
* Set the buffer threshold (BUFFER_THRESHOLD_TO_SYNC) to 3 reports for sync triggering. 
* Run the entire scenario once off-camera to verify:
  * timings,
  * expected failures,
  * fail-fast behavior (only the first violated check is logged).
* Record the full screen, **1920×1080** + **30 fps**,  and crop during editing if needed.
* If a step times out or fails unexpectedly, stop and re-record only that segment.

---

## Timeline breakdown

### 0:00 -> 0:02 - Title slide

* Visual: Slide with project title **“A53 – DeathNode”**, team ID, names.
* Subtitle:
  *DeathNode is an anonymous reporting platform. This two-minute demo shows secure report handling, consistency enforcement, and flooding mitigation.*

---

### 0:03 -> 0:05 - System startup and secure connections

* Visual: Slide title **Secure system startup**.

### 0:05 -> 0:10

* Show server and both nodes establishing connections.
* Emphasize that all communication occurs over authenticated secure channels (logs show that).

---

### 0:11 -> 0:13 - Local report creation

* Visual: Slide title **Local report creation (no synchronization yet)**.

### 0:14 -> 0:23

**Actions:**

1. On Node A: create 1 report
2. On Node B: create 1 report
3. On Node A: create another report
4. List reports on both nodes (including unprotect steps)

**What to show:**

* Reports are created locally and stored encrypted.
* No synchronization has occurred yet.
* Listing shows **unsynced reports** still buffered.
* Unprotect steps demonstrate:

  * integrity verification,
  * that only authorized nodes can decrypt reports (SR1, SR2) -> for that, show the envelope structure in on oof the created envelopes.

---

### 0:24 -> 0:26 - Normal synchronization

* Visual: Slide title **Normal synchronization protocol**.

### 0:27 -> 0:40

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

---

### 0:41 -> 0:43 - Tampering detection (AEAD)

* Visual: Slide title **Detection of tampered encrypted reports**.

### 0:44 -> 0:55

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

### 0:55 -> 0:57 - Server misbehavior simulation

* Visual: Slide title **Server misbehavior: order tampering**.

---

### 0:58 -> 1:10 - Detection of order tampering (SR3)

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

### 1:10 -> 1:12 - Diverging history simulation

* Visual: Slide title **Server misbehavior: diverging history**.

---

### 1:13 -> 1:30 - Detection of diverging history (SR4)

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

---

### 1:31 -> 1:33 - Flooding attack detection

* Visual: Slide title **Flooding attack and vigilant monitoring**.

---

### 1:34 -> 2:00 - Monitor enforcement (Challenge B)

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