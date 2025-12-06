package com.deathnode.client;

import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.Optional;

import com.deathnode.client.Config;

public class LocalDb {
    private final String url;

    public LocalDb() throws Exception {
        Path dbPath = Paths.get(Config.SQLITE_DB);
        Files.createDirectories(dbPath.getParent());
        this.url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
    }

    private Connection conn() throws SQLException {
        return DriverManager.getConnection(url);
    }

    /**
     * Return last node-local sequence number (0 if not present)
     */
    public long getLastSequenceNumber(String nodeId) throws SQLException {
        String query = "SELECT last_sequence_number FROM nodes_state WHERE node_id = ?";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(query)) {
            p.setString(1, nodeId);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) {
                    long v = rs.getLong(1);
                    if (rs.wasNull()) return 0L;
                    return v;
                }
                return 0L;
            }
        }
    }

    /**
     * Return last envelope hash (or null)
     */
    public String getLastEnvelopeHash(String nodeId) throws SQLException {
        String query = "SELECT last_envelope_hash FROM nodes_state WHERE node_id = ?";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(query)) {
            p.setString(1, nodeId);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1); // may be null
                }
                return null;
            }
        }
    }

    /**
     * Upsert node state (last_sequence_number, last_envelope_hash) -> if entry not present, insert it, else update it
     */
    public void upsertNodeState(String nodeId, long lastSequenceNumber, String lastEnvelopeHash) throws SQLException {
        String query = "INSERT INTO nodes_state(node_id, last_sequence_number, last_envelope_hash) VALUES(?,?,?) "
                   + "ON CONFLICT(node_id) DO UPDATE SET last_sequence_number = excluded.last_sequence_number, last_envelope_hash = excluded.last_envelope_hash";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(query)) {
            p.setString(1, nodeId);
            p.setLong(2, lastSequenceNumber);
            if (lastEnvelopeHash == null) p.setNull(3, Types.VARCHAR); else p.setString(3, lastEnvelopeHash);
            p.executeUpdate();
        }
    }

    /**
     * Insert a new local report entry (pending sync).
     */
    public void insertReport(String envelopeHash, String filePath, String signerId, long nodeSequenceNumber, String prevEnvelopeHash) throws SQLException {
        String query = "INSERT INTO reports(envelope_hash,file_path,signer_node_id,node_sequence_number,metadata_timestamp,prev_envelope_hash) VALUES(?,?,?,?,?,?)";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(query)) {
            p.setString(1, envelopeHash);
            p.setString(2, filePath);
            p.setString(3, signerId);
            p.setLong(4, nodeSequenceNumber);
            p.setString(5, Instant.now().toString());
            if (prevEnvelopeHash == null) p.setNull(6, Types.VARCHAR); else p.setString(6, prevEnvelopeHash);
            p.executeUpdate();
        }
    }

}
