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


-- nodes for testing (private keys of this machine's node is in the keystore)
INSERT OR REPLACE INTO nodes(node_id, enc_pub_key, sign_pub_key) -- self node
VALUES (
  '1.1.1.1',
  '-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAthoMm7MbxwtQDpJJhi9M
TuwM2gO4xiB5M3kG6+cnWfkG65CxGymGJazl0j/6HPdPxQnH/cpgfZuAGIon63uD
afWm3IxaWgeMt9++FuYrmSRj1iTcp7NnUFIxt3dpGgC7XGZurXB65VPcWH5FA2y6
B50QSNeYkDh9CjaFTm1Pk4nU0b/8YoJxtmf+RDik8yqao/d3zGS7KCqIplqudmx3
WpCgpbkdmMRSYKvU6XuF23Eef2FAtmyN/YKJRjrGOTn4Q/P8+lB7uIBub5LDtgtQ
7m/7LKZM2rLkeKGl3kJlcWcFXqVON8kWMTjSlsd9hrrcQbwyo57H+/PaPCFm8d23
BQIDAQAB
-----END PUBLIC KEY-----',
  '-----BEGIN PUBLIC KEY-----
MCowBQYDK2VwAyEAiNLejZvOkRf87o97eoq7rHEfcAXvoRHJR2GoBw1o5Rs=
-----END PUBLIC KEY-----'
);

INSERT OR REPLACE INTO nodes(node_id, enc_pub_key, sign_pub_key)
VALUES (
  '2.2.2.2',
  '-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAuBLlWP1ueNOoB70iONaA
PfImIUpzjfJkEwSrnvtaJ1GqiyaBxKKrK7D/GsNgdnTB7GAhHXJNuRUVLIUnmtEi
xROWO+RxgWyKWFmwGmDWJn3bdMXJUjjsUzjLb/EDF4P6GgYULmhEJfhKyBb719J+
fkmbEZjre4hnrk+ctLjJ2e7uW65u7y1KQ0khqp+WFvdX1KYtO76QB70MpPwnBoIi
vikYrEGO7phOl2Il4Cu/9DQ8qzS6h37XGZ/L+bi2VfAm6dxGlyRIIUEjvm+A94dP
ZJNehY118vMJVOKraJYqpq6TdNCv//Jqq4FR7umlyh7BKob8kq7kJxf/3p2Rg27c
hwIDAQAB
-----END PUBLIC KEY-----',
  'IGNORED_FOR_NOW'
);
