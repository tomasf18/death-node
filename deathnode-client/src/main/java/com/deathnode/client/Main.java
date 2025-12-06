package com.deathnode.client;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class Main {
    private static final Logger logger = LogManager.getLogger(Main.class);

    public static void main(String[] args) throws Exception {

        LocalDb db = new LocalDb();
        ClientService clientService = new ClientService(db);

        Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);

        logger.info("DeathNode client started.");
        logger.info("Type 'help' to see available commands.");

        while (true) {
            System.out.print("deathnode-client> ");

            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+");
            String command = parts[0];

            try {
                switch (command) {

                    case "create-report":
                        clientService.createReportInteractive(Config.NODE_ID);
                        break;

                    case "help":
                        printHelp();
                        break;

                    case "q":
                    case "exit":
                        logger.info("Shutting down client.");
                        return;

                    default:
                        logger.error("Unknown command: " + command);
                        printHelp();
                }

            } catch (Exception e) {
                logger.error("Command failed: " + command, e);
            }
        }
    }

    private static void printHelp() {
        System.out.println();
        System.out.println("Available commands:");
        System.out.println("  create-report     Create a new report interactively");
        System.out.println("  help              Show this help");
        System.out.println("  exit | q       Exit the client");
        System.out.println();
    }
}
