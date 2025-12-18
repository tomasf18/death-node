package com.deathnode.client.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import com.deathnode.client.config.Config;

public class ReportCleanupService {
    private final DatabaseService databaseService;

    public ReportCleanupService(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }

    public List<DatabaseService.ReportRow> getUnsyncedReports() throws SQLException {
        List<DatabaseService.ReportRow> reports = databaseService.listReports();
        List<DatabaseService.ReportRow> unsynced = new ArrayList<>();
        
        for (DatabaseService.ReportRow report : reports) {
            if (report.globalSequenceNumber == null) {
                unsynced.add(report);
            }
        }
        
        return unsynced;
    }

    public boolean deleteReportFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            if (Files.exists(path)) {
                Files.delete(path);
                System.out.println("Deleted report file: " + filePath);
                return true;
            }
            System.out.println("Report file does not exist: " + filePath);
            return false;
        } catch (Exception e) {
            System.err.println("Failed to delete report file " + filePath + ": " + e.getMessage());
            return false;
        }
    }

    public void cleanupReport(DatabaseService.ReportRow report) throws SQLException {
        System.out.println("Cleaning up unsynced report: " + report.envelopeHash);
        
        if (report.filePath != null) {
            deleteReportFile(report.filePath);
        }
        
        databaseService.deleteReportFromDatabase(report.envelopeHash);
    }

    public void rollbackSelfNodeState() throws SQLException {
        String selfNodeId = Config.getNodeSelfId();
        System.out.println("Rolling back self node state for node ID: " + selfNodeId);
        DatabaseService.ReportRow report = databaseService.getLastSyncedReport(selfNodeId);
        System.out.println("Last synced report: " + (report != null ? report.envelopeHash : "none") + ", global seq#: " + (report != null ? report.globalSequenceNumber : "N/A"));
        databaseService.upsertNodeState(selfNodeId, report != null ? report.globalSequenceNumber : 0, report != null ? report.envelopeHash : null);
    }

    public CleanupResult cleanupAllUnsyncedReports() throws SQLException {
        List<DatabaseService.ReportRow> unsyncedReports = getUnsyncedReports();
        
        System.out.println("Starting cleanup of " + unsyncedReports.size() + " unsynced reports");
        
        int filesDeleted = 0;
        int databaseRecordsDeleted = 0;
        List<String> failedDeletions = new ArrayList<>();
        
        for (DatabaseService.ReportRow report : unsyncedReports) {
            try {
                if (report.filePath != null && deleteReportFile(report.filePath)) {
                    filesDeleted++;
                }
                
                databaseService.deleteReportFromDatabase(report.envelopeHash);
                databaseRecordsDeleted++;
                
            } catch (SQLException e) {
                System.err.println("Failed to cleanup report " + report.envelopeHash + ": " + e.getMessage());
                failedDeletions.add(report.envelopeHash);
            }
        }

        rollbackSelfNodeState();
        
        System.out.println("Cleanup completed: " + filesDeleted + " files deleted, " + databaseRecordsDeleted + " database records deleted");
        
        return new CleanupResult(unsyncedReports.size(), filesDeleted, databaseRecordsDeleted, failedDeletions);
    }

    /**
     * Result of a cleanup operation.
     */
    public static class CleanupResult {
        private final int totalReportsFound;
        private final int filesDeleted;
        private final int databaseRecordsDeleted;
        private final List<String> failedDeletions;

        public CleanupResult(int totalReportsFound, int filesDeleted, int databaseRecordsDeleted, List<String> failedDeletions) {
            this.totalReportsFound = totalReportsFound;
            this.filesDeleted = filesDeleted;
            this.databaseRecordsDeleted = databaseRecordsDeleted;
            this.failedDeletions = failedDeletions;
        }

        public int getTotalReportsFound() { return totalReportsFound; }
        public int getFilesDeleted() { return filesDeleted; }
        public int getDatabaseRecordsDeleted() { return databaseRecordsDeleted; }
        public List<String> getFailedDeletions() { return failedDeletions; }
        public boolean isComplete() { return failedDeletions.isEmpty(); }
    }
}
