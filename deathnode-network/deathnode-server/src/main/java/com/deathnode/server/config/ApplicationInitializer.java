package com.deathnode.server.config;

import com.deathnode.server.service.DatabaseManager;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class ApplicationInitializer {

    private final DatabaseManager databaseManager;

    public ApplicationInitializer(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Run database reinitialization when the Spring application is ready.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        System.out.println("=".repeat(60));
        System.out.println("Initializing Server...");
        System.out.println("=".repeat(60));
        
        try {
            databaseManager.reinitializeDatabase();
            System.out.println("=".repeat(60));
            System.out.println("Server initialization completed successfully");
            System.out.println("=".repeat(60));
        } catch (Exception e) {
            System.err.println("=".repeat(60));
            System.err.println("CRITICAL: Failed to initialize database on startup");
            System.err.println("Error: " + e.getMessage());
            System.err.println("=".repeat(60));
            e.printStackTrace();
            throw new RuntimeException("Database initialization failed on startup", e);
        }
    }
}
