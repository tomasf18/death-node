package com.deathnode.client.config;

public class Config {
    public static final String NODE_SELF_ID = "1.1.1.1";  // self node id
    public static final String NODE_PSEUDONYM = "shadow_fox";
    
    public static final String SERVER_HOST = "localhost"; // adapt for VMs later
    public static final int SERVER_PORT = 9090;
    
    public static final String ENVELOPES_DIR = "data/envelopes";
    public static final String SQLITE_DB = "data/client.db";
    public static final String SCHEMA_SQL = "client_schema.sql";
    public static final int BUFFER_THRESHOLD_TO_SYNC = 5; // max buffered reports before triggering sync
    
    public static final String KEYSTORE_PATH = "keys/keystore.jks";
    public static final String KEYSTORE_PASSWORD = "demonstration";  // just for the project demo
    public static final String ED_PRIVATE_KEY_ALIAS = "sign-key";    // alias under which Ed25519 private key is stored
    public static final String RSA_PRIVATE_KEY_ALIAS = "rsa-key";    // alias under which RSA private key is stored

    private Config() {
        // empty
    }
}
