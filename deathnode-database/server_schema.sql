CREATE TABLE IF NOT EXISTS nodes (
    node_id                 VARCHAR(255)                PRIMARY KEY,
    pseudonym               TEXT                        NOT NULL,
    enc_pub_key             VARCHAR(900)                NOT NULL,      -- RSA public key
    sign_pub_key            VARCHAR(300)                NOT NULL       -- Ed25519 public key
);

CREATE TABLE IF NOT EXISTS nodes_sync_state (
    node_id                 VARCHAR(255)                PRIMARY KEY,
    last_sequence_number    BIGINT,
    last_envelope_hash      VARCHAR(64),
    FOREIGN KEY (node_id) REFERENCES nodes(node_id)
);

CREATE TABLE IF NOT EXISTS reports (
    envelope_hash           VARCHAR(64)                 PRIMARY KEY,
    signer_node_id          VARCHAR(255)                NOT NULL,
    node_sequence_number    BIGINT                      NOT NULL,  -- assigned by signer node
    global_sequence_number  BIGINT, -- assigned by server; null for unsynced reports
    metadata_timestamp      TIMESTAMP WITH TIME ZONE    NOT NULL,
    prev_report_hash        VARCHAR(64),
    file_path               TEXT                        NOT NULL,
    UNIQUE (signer_node_id, node_sequence_number),
    FOREIGN KEY (signer_node_id) REFERENCES nodes(node_id)
);

CREATE TABLE IF NOT EXISTS signed_block_merkle_roots (
    block_id                BIGSERIAL                   PRIMARY KEY, -- auto-incremented
    block_number            BIGINT                      NOT NULL        UNIQUE,
    block_root              VARCHAR(64)                 NOT NULL        UNIQUE,
    per_node_roots_json     JSONB                       NOT NULL,
    prev_block_root         VARCHAR(64),
    server_signature        VARCHAR(64)                 NOT NULL
);
