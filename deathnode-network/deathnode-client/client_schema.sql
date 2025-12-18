PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS nodes (
    node_id                 TEXT        PRIMARY KEY,
    enc_pub_key             TEXT        NOT NULL,      
    sign_pub_key            TEXT        NOT NULL      
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
    node_sequence_number    BIGINT      NOT NULL,
    global_sequence_number  BIGINT,
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

INSERT OR REPLACE INTO nodes(node_id, enc_pub_key, sign_pub_key) 
VALUES (
  'nodeA',
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

INSERT OR REPLACE INTO nodes(node_id, enc_pub_key, sign_pub_key)
VALUES (
  'nodeB',
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

INSERT OR REPLACE INTO nodes(node_id, enc_pub_key, sign_pub_key)
VALUES (
  'server',
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