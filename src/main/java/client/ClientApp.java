package client;

import entity.Report;
import service.ReportDatabase;

import java.sql.SQLException;
import java.util.Iterator;

public class ClientApp {

    private final ReportDatabase reportDB;
    private final int localVersion;

    public ClientApp() {
        reportDB = new ReportDatabase();
        localVersion = 0;
    }

    public void storeReport(Report report) {
        reportDB.addReport(report);
    }

    public int getLocalVersion() {
        return localVersion;
    }
}
