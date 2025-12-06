package com.deathnode.client;

import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.logging.Logger;
import java.sql.Timestamp;

public class LocalDb {
    private static final Logger logger = Logger.getLogger(LocalDb.class.getName());
    private final String url;

    public LocalDb() throws Exception {
        Path dbPath = Paths.get(Config.SQLITE_DB);
        Files.createDirectories(dbPath.getParent());
        this.url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        initSchema();
    }

    private Connection conn() throws SQLException {
        return DriverManager.getConnection(url);
    }

    private void initSchema() throws Exception {
        Path schema = Paths.get("client_schema.sql");
        if (!Files.exists(schema)) {
            throw new RuntimeException("client_schema.sql missing in working dir");
        }
        String sql = Files.readString(schema);
        try (Connection c = conn(); Statement s = c.createStatement()) {
            s.executeUpdate("PRAGMA foreign_keys=ON;");
            for (String stmt : sql.split(";")) {
                String t = stmt.trim();
                if (!t.isEmpty()) s.executeUpdate(t);
            }
        }
    }

    public void insertReport(String envelopeHash, String filePath, String signerId, long seq, byte[] prevHash) throws SQLException {
        String sql = "INSERT INTO reports(envelope_hash,file_path,signer_node_id,sequence_number,prev_report_hash,metadata_timestamp) VALUES(?,?,?,?,?,?)";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, envelopeHash);
            p.setString(2, filePath);
            p.setString(3, signerId);
            p.setLong(4, seq);
            p.setBytes(5, prevHash);
            p.setTimestamp(6, Timestamp.from(Instant.now()));   
            p.executeUpdate();
        }
    }
    public void listReports() throws SQLException {
        String sql = "SELECT envelope_hash, signer_node_id, sequence_number, file_path, accepted FROM reports ORDER BY stored_at DESC";
        try (Connection c = conn(); Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) {
            logger.info("Local reports:");
            while (rs.next()) {
                logger.info(String.format("%s | %s | seq=%d | %s | accepted=%d",
                        rs.getString("envelope_hash"),
                        rs.getString("signer_node_id"),
                        rs.getLong("sequence_number"),
                        rs.getString("file_path"),
                        rs.getInt("accepted")));
            }
        }
    }
}
