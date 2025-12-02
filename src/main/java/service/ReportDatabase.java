package service;

import entity.Report;

import java.sql.*;
import java.util.*;

public class ReportDatabase {

    private Connection conn;
    private static final String DB_PATH = "deathnode.db";

    public ReportDatabase() {
        try {
            initDatabase();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initDatabase() throws SQLException {
            conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);

            // Tabela de reports
            String createReportsTable = """
            CREATE TABLE IF NOT EXISTS encrypted_reports (
                report_id TEXT PRIMARY KEY,
                timestamp TEXT NOT NULL,
                author TEXT NOT NULL,
                encrypted_content TEXT NOT NULL
            )
        """;
        Statement stmt = conn.createStatement();
        stmt.execute(createReportsTable);
        stmt.close();
    }

    public void addReport(Report report) throws SQLException {
            String sql = """
            INSERT INTO encrypted_reports 
            (report_id, timestamp, author, encrypted_content)
            VALUES (?, ?, ?, ?)
        """;

            try (PreparedStatement pstmt = conn.prepareStatement(sql)){
                pstmt.setString(1, report.getId());
                pstmt.setString(2, report.getTimestamp());
                pstmt.setString(3, report.getAuthor());
                pstmt.setString(4, report.getContent());
                int rowsAffected = pstmt.executeUpdate();

                if (rowsAffected == 0) {
                    throw new SQLException("Failed to insert report - no rows affected");
                }
            } catch (SQLIntegrityConstraintViolationException e) {
            throw new SQLException("Report with ID '" + report.getId() + "' already exists", e);
        }
    }

    public Report getReport(String id) {
        String sql = """
            SELECT report_id, timestamp, author, encrypted_content
            FROM encrypted_reports 
            WHERE report_id = ?
        """;

        try(PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, id);

            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                return new Report(
                        rs.getString("report_id"),
                        rs.getString("timestamp"),
                        rs.getString("author"),
                        rs.getString("encrypted_content"));
            }
            rs.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public Iterator<Report> getAllReports() throws SQLException {
        String sql = """
            SELECT report_id, timestamp, author, encrypted_content
            FROM encrypted_reports 
            ORDER BY timestamp DESC
        """;

        List<Report> reports = new ArrayList<>();

        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery(sql);

        while (rs.next()) {
            Report report = new Report(
                    rs.getString("report_id"),
                    rs.getString("timestamp"),
                    rs.getString("author"),
                    rs.getString("encrypted_content"));
            reports.add(report);
        }

        rs.close();
        stmt.close();
        return reports.iterator();
    }

}
