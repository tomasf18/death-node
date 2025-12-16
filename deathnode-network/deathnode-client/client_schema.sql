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

INSERT OR REPLACE INTO nodes(node_id, enc_pub_key, sign_pub_key)
VALUES (
  'nodeB',
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

INSERT OR REPLACE INTO nodes(node_id, enc_pub_key, sign_pub_key)
VALUES (
  'server',
  '-----BEGIN PUBLIC KEY-----
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAhp9Pnb0lsXqcZrfslTx6
yE9YMCtPhA6AFaQAel6jB+RLi9nBjzL7eROltXrCImIxxI6pDGIrYbCuq6eTSOwF
8DXLjCgYch6lznqJ1dPFaMpwyJKnKJWxmzoJWEnoaC5edAQTnMsjNzVaPB62deRr
2ZOpzh2DzWpFIXg4vOx7FMOZeusR329Yw3Kt9V3rx3/DRfMr+l1GuygnSod0XIFu
qo12neqUCa9Q7t8BdN3111OI79ZoE9vFTlGCoZbpINt7ZZqgpXWb+RwH4xcJUTsB
DR3AvJ05e+aUqGEoG9LmdsUXfTstNnElaTy3UZA9oq1zh6bGFKkRS/jMAy3QRi9f
qwIDAQAB
-----END PUBLIC KEY-----',
  '-----BEGIN PUBLIC KEY-----
MCowBQYDK2VwAyEAbaWBhFynZUSfVl1BGxDU3LoQKiJ+gpgRQPI/flM3Ld8=
-----END PUBLIC KEY-----'
);