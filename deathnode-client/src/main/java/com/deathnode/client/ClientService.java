package com.deathnode.client;

import com.google.gson.Gson;
import java.io.*;
import java.net.http.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

public class ClientService {
    private final LocalDb db;
    private final HttpClient http = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    public ClientService(LocalDb db) {
        this.db = db;
    }

    public String createReport(String signerId, long seq) throws Exception {
        Files.createDirectories(Paths.get(Config.ENVELOPES_DIR));
        // Dummy payload: later this will be an AEAD envelope (JSON)
        String content = "report body created at " + System.currentTimeMillis() + " by " + signerId;
        byte[] bytes = content.getBytes();

        String hash = HashUtils.sha256Hex(bytes);
        String filename = hash + ".env";
        Path p = Paths.get(Config.ENVELOPES_DIR, filename);
        Files.write(p, bytes, StandardOpenOption.CREATE_NEW);

        // prev_hash - baseline: zero bytes
        byte[] prev = new byte[32];

        db.insertReport(hash, p.toString(), signerId, seq, prev);
        System.out.println("Created envelope: " + filename);
        return hash;
    }

    public void list() throws Exception {
        db.listReports();
    }

    public void sync(String nodeId) throws Exception {
        // prepare buffer: for baseline, include all non-accepted reports
        List<Map<String,Object>> buffer = new ArrayList<>();
        try (ConnectionlessReports cr = new ConnectionlessReports()) {
            buffer = cr.getPendingReports();
        }

        Map<String,Object> payload = new HashMap<>();
        payload.put("nodeId", nodeId);
        payload.put("buffer", buffer);

        String json = gson.toJson(payload);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(Config.SERVER_BASE + "/sync"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("Server response: " + resp.statusCode() + " -> " + resp.body());

        // TODO: parse response, update local block state, mark accepted reports
    }

    // small helper to read pending reports without adding a heavy DAO in this sample
    private class ConnectionlessReports implements AutoCloseable {
        public List<Map<String,Object>> getPendingReports() throws Exception {
            List<Map<String,Object>> list = new ArrayList<>();
            // quick-and-dirty JDBC read to collect pending reports
            java.sql.Connection c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + Paths.get(Config.SQLITE_DB).toAbsolutePath());
            try (java.sql.PreparedStatement p = c.prepareStatement("SELECT envelope_hash, file_path, signer_node_id, sequence_number FROM reports WHERE accepted=0")) {
                try (java.sql.ResultSet rs = p.executeQuery()) {
                    while (rs.next()) {
                        Map<String,Object> row = new HashMap<>();
                        row.put("envelopeHash", rs.getString("envelope_hash"));
                        row.put("filePath", rs.getString("file_path"));
                        row.put("sequence", rs.getLong("sequence_number"));
                        row.put("signer", rs.getString("signer_node_id"));
                        list.add(row);
                    }
                }
            } finally {
                c.close();
            }
            return list;
        }
        public void close() {}
    }
}
