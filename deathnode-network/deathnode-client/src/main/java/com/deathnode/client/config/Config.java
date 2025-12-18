package com.deathnode.client.config;

public class Config {
    private static String NODE_SELF_ID = "nodeZ";  // default node id
    private static String NODE_PSEUDONYM = null;     // generated randomly if not set
    
    // Server configuration
    public static final String SERVER_NODE_ID = "server";
    public static final String SERVER_HOST = "127.0.0.1"; // localhost
    // public static final String SERVER_HOST = "192.168.0.10"; // VM
    public static final int SERVER_PORT = 9090;
    
    // Sync configuration
    public static final int BUFFER_THRESHOLD_TO_SYNC = 2; // max buffered reports before triggering sync
    
    // Keystore configuration
    public static final String KEYSTORE_PASSWORD = "demonstration";  // just for the project demo
    public static final String ED_PRIVATE_KEY_ALIAS = "sign-key";    // alias under which Ed25519 private key is stored
    public static final String RSA_PRIVATE_KEY_ALIAS = "rsa-key";    // alias under which RSA private key is stored

    // Security tool config
    public static final String ENCRYPTION_KEYS_ALG = "RSA";
    public static final String SIGNING_KEYS_ALG = "Ed25519";
    
    // Database schema location
    public static final String SCHEMA_SQL = "client_schema.sql";
    
    // Paths
    public static String ENVELOPES_DIR = getEnvelopesDir();
    public static String SQLITE_DB = getSqliteDb();
    public static String KEYSTORE_PATH = getKeystorePath();

    public static void initialize(String nodeId, String pseudonym) {
        NODE_SELF_ID = nodeId;
        NODE_PSEUDONYM = pseudonym != null ? pseudonym : generateRandomPseudonym();
    }

    public static String getNodeSelfId() {
        return NODE_SELF_ID;
    }

    public static String getNodePseudonym() {
        return NODE_PSEUDONYM != null ? NODE_PSEUDONYM : "default_node";
    }

    public static String getEnvelopesDir() {
        return "client-data/" + NODE_SELF_ID + "/envelopes/";
    }

    public static String getSqliteDb() {
        return "client-data/" + NODE_SELF_ID + "/client.db";
    }

    public static String getKeystorePath() {
        return "client-data/" + NODE_SELF_ID + "/keys/keystore.jks";
    }

    public static String getNodeKeysDir() {
        return "client-data/" + NODE_SELF_ID + "/keys/";
    }

    public static String getCaCertificate() {
        return getNodeKeysDir() + "ca-cert.pem";
    }

    public static String getSelfCertificate() {
        return getNodeKeysDir() + "tls-cert.pem";
    }

    public static String getSelfGrpcPrivateKey() {
        return getNodeKeysDir() + "tls-key.pem";
    }

    private static String generateRandomPseudonym() {
        StringBuilder sb = new StringBuilder();
        String chars = "abcdefghijklmnopqrstuvwxyz_0123456789";
        java.util.Random rand = new java.util.Random();
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(rand.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private Config() {
        // empty
    }
}
