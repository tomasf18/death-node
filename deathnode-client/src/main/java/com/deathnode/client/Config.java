package com.deathnode.client;

public class Config {
    public static final String NODE_ID = "1.1.1.1";
    public static final String ENVELOPES_DIR = "data/envelopes";
    public static final String SQLITE_DB = "data/client.db";
    public static final String SCHEMA_SQL = "client_schema.sql";
    public static final String SERVER_BASE = "http://localhost:8080"; // adapt for VMs later
    public static final int BUFFER_SIZE = 10; // max buffered reports to send per sync
    public static final String NODE_PSEUDONYM = "shadow_fox";
    
    private Config() {
        // empty
    }
}
