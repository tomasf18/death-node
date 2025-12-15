package com.deathnode.client.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.sql.*;

import java.time.Instant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.deathnode.client.config.Config;

public class DatabaseService {
    private final String url;

    public DatabaseService() throws Exception {
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

    public String getEncPubKey(String nodeId) throws SQLException {
        String sql = "SELECT enc_pub_key FROM nodes WHERE node_id = ?";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, nodeId);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) return rs.getString(1);
                return null;
            }
        }
    }

    public Map<String, String> getAllEncPubKeys() throws SQLException {
        String sql = "SELECT node_id, enc_pub_key FROM nodes";
        Map<String, String> map = new LinkedHashMap<>();
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql); ResultSet rs = p.executeQuery()) {
            while (rs.next()) {
                String nodeId = rs.getString(1);
                String enc = rs.getString(2);
                map.put(nodeId, enc);
            }
        }
        return map;
    }

    public String getSignPubKey(String nodeId) throws SQLException {
        String sql = "SELECT sign_pub_key FROM nodes WHERE node_id = ?";
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql)) {
            p.setString(1, nodeId);
            try (ResultSet rs = p.executeQuery()) {
                if (rs.next()) return rs.getString(1);
                return null;
            }
        }
    }

    public List<ReportRow> listReports() throws SQLException {
        String sql = "SELECT envelope_hash, signer_node_id, node_sequence_number, metadata_timestamp, prev_envelope_hash, file_path FROM reports ORDER BY node_sequence_number ASC";
        List<ReportRow> list = new ArrayList<>();
        try (Connection c = conn(); PreparedStatement p = c.prepareStatement(sql); ResultSet rs = p.executeQuery()) {
            while (rs.next()) {
                ReportRow r = new ReportRow();
                r.envelopeHash = rs.getString(1);
                r.signerNodeId = rs.getString(2);
                r.nodeSequenceNumber = rs.getLong(3);
                r.metadataTimestamp = rs.getString(4);
                r.prevEnvelopeHash = rs.getString(5);
                r.filePath = rs.getString(6);
                list.add(r);
            }
        }
        return list;
    }

    /** Simple row holder for report listings. */
    public static class ReportRow {
        public String envelopeHash;
        public String signerNodeId;
        public long nodeSequenceNumber;
        public String metadataTimestamp;
        public String prevEnvelopeHash;
        public String filePath;
    }

    /**
     * DEBUGGING ONLY: Reset the local database (delete all data).
     */
    public void resetDatabase() throws IOException, SQLException {
        String sql1 = "DELETE FROM reports";
        String sql2 = "DELETE FROM nodes_state";
        String sql3 = "DELETE FROM block_state";
        try (Connection c = conn(); 
             PreparedStatement p1 = c.prepareStatement(sql1);
             PreparedStatement p2 = c.prepareStatement(sql2);
             PreparedStatement p3 = c.prepareStatement(sql3)) {
            p1.executeUpdate();
            p2.executeUpdate();
            p3.executeUpdate();
        }

        // delete all envelope files
        Path envelopesDir = Paths.get(Config.ENVELOPES_DIR);
        if (Files.exists(envelopesDir) && Files.isDirectory(envelopesDir)) {
            try (var stream = Files.list(envelopesDir)) {
                stream.forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (Exception e) {
                        // ignore
                    }
                });
            }
        }
    }
}