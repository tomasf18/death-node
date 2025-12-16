DROP TABLE IF EXISTS signed_block_merkle_roots;;
DROP TABLE IF EXISTS reports;
DROP TABLE IF EXISTS nodes_sync_state;
DROP TABLE IF EXISTS nodes;

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
    block_id                    BIGSERIAL                   PRIMARY KEY, -- auto-incremented
    block_number                BIGINT                      NOT NULL        UNIQUE,
    block_root                  VARCHAR(64)                 NOT NULL        UNIQUE,
    prev_block_root             VARCHAR(64)
);

INSERT INTO nodes(node_id, pseudonym, enc_pub_key, sign_pub_key) 
VALUES (
  'nodeA',
  'AlphaNode',
  '-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA2Ub5h/yfqbaVXH5Emy4e
EnPXyURp/8klVFJ/baDEthfsChrENdREERp1xCA5/6YuYVeiAJQ6rMJkFHjMLDnc
mBnCAx2/Fb542IZrAM7DGI+pQPp3gbt9WMq46gW6tyvRFupi+whEca4Xnckw55qM
eZE7yX+Fqv8ekmPiNcikRjWYgucBoGib0QSQC0ThIl4rn/AWc8USTcOukziIlToS
rfZK7aIGnwq6yM6aBq5HhNWQpsg5pqyZtrUFYurgycehh1qNuA0ILqwcgf7QAOrW
mDn6CoSYks1UiJASJ2al0wp8/Fj35gVpPV2NaChd6QynjsmgV5LjOtNPj22nwhY/
jwIDAQAB
-----END PUBLIC KEY-----',
  '-----BEGIN PUBLIC KEY-----
MCowBQYDK2VwAyEAxp6F4FBZ3pb47kGYxmcvsAXCq6p+Uv6n26iyI1aVs0E=
-----END PUBLIC KEY-----'
);

INSERT INTO nodes(node_id, pseudonym, enc_pub_key, sign_pub_key)
VALUES (
  'nodeB',
  'BetaNode',
  '-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvt7yJvvyjRUw2hHDv27m
Tz4teEkEWQDCHV1gBZlC75tPHMvwLBVsZJ2I9KEdeP/FiPA0b+hkiO//4s/DzJ2W
hXTYeVlYl/D8nnTPJ6xd2I38dfKLPD3bWBViVhfLZ+q56Bgh0J0FS4e+wGaqRrZ5
D4MxjtCPQLUaGB7mJUW5Xum2IFopz7fWmd0lcAG3XqRSnxNAntRpUCK0Z5epmTin
bqYj7DbPCOMJt47qbNnSr2m1RkdbfSz1XpERZjeNA5Ysj0ZHtrs7ypyk7D24Aj+1
olv60yS7CzmblFj8ylKPlyJRihm5FjNhW+7r4N7DcEmfv9K7S1TQfm+XkZW6FOrY
3wIDAQAB
-----END PUBLIC KEY-----',
  '-----BEGIN PUBLIC KEY-----
MCowBQYDK2VwAyEAbpf4IpdEFv/Az6nW5u7vE/j8q5oT2aDIcIVmKFheE8w=
-----END PUBLIC KEY-----'
);

INSERT INTO nodes_sync_state(node_id, last_sequence_number, last_envelope_hash)
VALUES ('nodeA', NULL, NULL), ('nodeB', NULL, NULL);
