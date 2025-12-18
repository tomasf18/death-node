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
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEApnmaaznaqhuJcc7HOaBe
dzIbBsbC3r87yIlDlSFZ9MSYGfQZ9lMD7tagQSMKxq3Runrj33VHTXUmP21KsRGB
PjdfiPK2Watl21qJBXP80ZKpXOHj8OWf2Ze0l33/YiGOrDaeCxwABUrSNkDC9U7w
3KKAfWo6Vpdc++bZQhMZdsKSJwAEMt0YMRk3hqgYAj1g6ObsXLYj7dii5gkpRM9+
xpH2JEZSkBTDS3HP7qW8Ddsf2A4zziLZrk0jJJXoNewsIEJduYq+27F3OpETA7pe
hRM5PkIgPKcr4jarGUrgjqRpSKbOuiNK2GfS8c0btS0Hx1sj3tJNxbSiWmUIZULf
AQIDAQAB
-----END PUBLIC KEY-----',
  '-----BEGIN PUBLIC KEY-----
MCowBQYDK2VwAyEAuIHZHPSNDaVyR2Ct28RFDOSYSz10bxxOyEL7kADrXm4=
-----END PUBLIC KEY-----'
);

INSERT INTO nodes(node_id, pseudonym, enc_pub_key, sign_pub_key)
VALUES (
  'nodeB',
  'BetaNode',
  '-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAyXzr8GzekSw2KN5wY0OG
EgCQdhDrEomEawxSBdojFsIWV/WCDJJK5W9a8ouzdRhfMgm9wEmm8wO2NZ8vQ41C
b0kN/p3eHICXh98/IwOchnGSMmqd2KbWM3zmcyaifiJbELRURSnwD42oXqyl9nkd
WTvSqBOLca+aVGd/SdQU0bcgUBsN2tZJYEpl4B+X//IvNB0EobzaY3PXbofgs2uM
2R3HX3rBFGkB5Dbb+DFIBuw+o8y1tJT6RfCn6Ig9FL+6EURLEB4BR9gCHf3+QpI9
dn6TcVfr9srlB93rmUevHhPCDXeOacUxPX3HJqRyg+ncgd5/mxkC7hc1jGtzuZCK
KwIDAQAB
-----END PUBLIC KEY-----',
  '-----BEGIN PUBLIC KEY-----
MCowBQYDK2VwAyEAXtkqAvGFkngik1IIWl3XQPlPP28Zc+HsM/Rd7NaraOU=
-----END PUBLIC KEY-----'
);

INSERT INTO nodes(node_id, pseudonym, enc_pub_key, sign_pub_key)
VALUES (
  'server',
  'ServerNode',
  '-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAt0rmK5JlSG5v+NNIlu6Z
rtfgfG8tqkClpUMAv9SsDobDqTX0CNcGVIEyJ3aqPMuzNFmk9CnsgjOC8I9BgfVC
CJIoTk65Y8QMXdZNGZ5uMms0wioDaTiLtWWkkUA4uClB0MVbhcsZtj3KPoD9J/5N
6eKGNsiLOdF9SC0jVLgUyy8FYWEEj2B+mxkrIkMUK0wjEyV8+rmWnuiEiQnPHjRU
SBPon/2KxfBB3oQ2ggFpdCV07ZfVKssEKJOX4mAcWgLh6xbd1N/gwibV3lDa9izS
vkSl65KdjrcqNbPfLcpEPdYXDnkJvkK5+l02E3JpQfeyYEeCQ0b+rXYBgnU90PN4
DQIDAQAB
-----END PUBLIC KEY-----',
  '-----BEGIN PUBLIC KEY-----
MCowBQYDK2VwAyEAIgRC6jVegjp3q66L0izgWYsxIyvNc2joyT11DQVwQ6A=
-----END PUBLIC KEY-----'
);