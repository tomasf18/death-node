PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS nodes (
    node_id                 TEXT        PRIMARY KEY,
    enc_pub_key             TEXT        NOT NULL,      -- RSA public key
    sign_pub_key            TEXT        NOT NULL       -- Ed25519 public key
);

CREATE TABLE IF NOT EXISTS nodes_state (
    node_id                 TEXT        PRIMARY KEY,
    last_sequence_number    BIGINT,
    last_envelope_hash      TEXT,
    FOREIGN KEY (node_id) REFERENCES nodes(node_id)
);

CREATE TABLE IF NOT EXISTS reports (
    envelope_hash           TEXT        PRIMARY KEY,
    signer_node_id          TEXT        NOT NULL,
    node_sequence_number    BIGINT      NOT NULL,  -- assigned by signer node
    global_sequence_number  BIGINT, -- assigned by server; null for unsynced reports
    metadata_timestamp      TIMESTAMP   NOT NULL,
    prev_envelope_hash      TEXT,
    file_path               TEXT        NOT NULL,
    FOREIGN KEY (signer_node_id) REFERENCES nodes(node_id),
    UNIQUE (signer_node_id, node_sequence_number)
);

CREATE TABLE IF NOT EXISTS block_state (
    id                      INTEGER     PRIMARY KEY,
    last_block_number       BIGINT,
    last_block_root         TEXT,
    CHECK (id = 1)
);


-- local node entry for testing
INSERT OR IGNORE INTO nodes(node_id, enc_pub_key, sign_pub_key)
VALUES ('1.1.1.1', '00', '00');
