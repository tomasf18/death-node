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
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAxYFjFwNE2o8RCd1OWzLI
ame9NjjKMOlU4tjfN+mz5bOOZznTgb0veNXfsi9xk5NHpg4zpg91nwc6sxHy0SWp
Qlh7z3bsJNIFgxK5qbu2Z7mw/WVbsiNC/5+KCIz99q1nCEomrVvmYBeWt95aX5Mp
OljPVRr6xexGOXoupdA+Rfoa8FGMP5PFO3FLYR6dd/K3wHRdwVtDA5rrb2zETG8i
uhgPHoxtUMuus0KhPSwXQSZcSdoPBdhMpPmkBt8XcrEii7cCr1yJPaVtnFdOapMH
omKCCp8DMjVIymsCQHjiX7C+kOyCcEYhXhGm+jRNwT1NJJkLa1aN93qP90O+jrvu
twIDAQAB
-----END PUBLIC KEY-----',
  '-----BEGIN PUBLIC KEY-----
MCowBQYDK2VwAyEAQ3l8GS+qcnCT8xOggUhcQnPmb/Y9iTBsiLpXJ2FDbKo=
-----END PUBLIC KEY-----'
);

INSERT OR REPLACE INTO nodes(node_id, enc_pub_key, sign_pub_key)
VALUES (
  '2.2.2.2',
  '-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAooKmovZljdcDK7EwA+pU
ez/bOw8cO7wicf4gfJCS8blylnckpTSAwkiPzF3HgjDFiU1s8f/v3d2aoAUHoLzo
4hzOKMYgymZs/I/ruxpUZMErE2IqJJ+hBOlVrILkjBhgq3eWYbyJ05UautIoTtQQ
lcYUUDHnnOqXx+BuV3L//DxQw0ppFrwHr2G+l7oRGfCx0YreMGGa7ZNe9inout1m
WXHll7V+YcuDYs0gI1KSpEziGEpGTiD8YIJEe9PrRFKmeZJBvnUqZ32YeXcpG+rH
8FtyU5EuNaVFMKPHMQUNrRMKAu4bdhXcMGjBQXMJuSQKdkouVxWD+NbadD/AkrYF
fQIDAQAB
-----END PUBLIC KEY-----',
  'IGNORED_FOR_NOW'
);
