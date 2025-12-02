package server;

import entity.Report;
import service.ReportDatabase;

import java.sql.SQLException;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicInteger;

public class ServerApp {

    private final ReportDatabase reportDB;
    private final AtomicInteger version;

    public ServerApp() {
        reportDB = new ReportDatabase();
        version = new AtomicInteger(0);
    }

    public int getVersion() {
        return version.get();
    }

    public int createReport(Report report) {

        // validar


        try {
            reportDB.addReport(report);
        } catch (SQLException e) {
            return -1;
        }
        return version.incrementAndGet();
    }

    public void listReports() {
        System.out.println("Listing reports");
        Iterator<Report> it;
        try {
            it = reportDB.getAllReports();
        } catch (SQLException e) {
            System.out.println("Listing reports\n" + e.getMessage());
            return;
        }
        while (it.hasNext()) {
            Report report = it.next();
            System.out.printf("[%s by %s (Created at %s)] %s\n", report.getId(), report.getAuthor(), report.getTimestamp(),  report.getContent());
        }
    }

    public Iterator<Report> getReports(int start, int end) {
        try {
            return reportDB.getAllReports();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
