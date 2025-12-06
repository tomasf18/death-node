CREATE TABLE IF NOT EXISTS nodes (
    node_id                 VARCHAR(255)        PRIMARY KEY,
    pseudonym               TEXT                NOT NULL,
    password_hash           BYTEA               NOT NULL,
    enc_pub_key             BYTEA               NOT NULL,      -- RSA public key
    sign_pub_key            BYTEA               NOT NULL       -- Ed25519 public key
);

CREATE TABLE IF NOT EXISTS nodes_sync_state (
    node_id                 VARCHAR(255)        PRIMARY KEY     REFERENCES nodes(node_id),
    last_sequence_number    BIGINT,
    last_envelope_hash      BYTEA
);

CREATE TABLE IF NOT EXISTS reports (
    envelope_hash           VARCHAR(64)                 PRIMARY KEY,
    signer_node_id          VARCHAR(255)                NOT NULL        REFERENCES nodes(node_id),
    node_sequence_number    BIGINT                      NOT NULL,  -- assigned by signer node
    global_sequence_number  BIGINT, -- assigned by server; null for unsynced reports
    metadata_timestamp      TIMESTAMP WITH TIME ZONE    NOT NULL,
    prev_report_hash        BYTEA,
    file_path               TEXT                        NOT NULL,
    UNIQUE (signer_node_id, node_sequence_number)
);

CREATE TABLE IF NOT EXISTS signed_block_merkle_roots (
    block_id                BIGINT              PRIMARY KEY, -- auto-incremented
    block_number            BIGINT              NOT NULL        UNIQUE,
    block_root              BYTEA               NOT NULL        UNIQUE,
    per_node_roots_json     JSONB               NOT NULL,
    prev_block_root         BYTEA,
    server_signature        BYTEA               NOT NULL
);
