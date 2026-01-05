package com.deathnode.client;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import com.deathnode.client.config.Config;
import com.deathnode.client.service.ClientService;
import com.deathnode.client.service.DatabaseService;

public class Main {

    public static void main(String[] args) throws Exception {
        String nodeId = null;
        String pseudonym = null;

        if (args.length > 0) {
            nodeId = args[0];
        }
        if (args.length > 1) {
            pseudonym = args[1];
        }

        if (nodeId == null) {
            printUsage();
            return;
        }

        Config.initialize(nodeId, pseudonym);
        
        System.out.println("DeathNode Client - Node: " + Config.getNodeSelfId() + " (" + Config.getNodePseudonym() + ")");
        System.out.println();

        DatabaseService db = new DatabaseService();
        db.resetDatabase(); 
        ClientService clientService = new ClientService(db);
        
        Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);

        System.out.println("DeathNode client started.");
        System.out.println("Type 'help' to see available commands.");

        while (true) {
            System.out.print("\n\ndeathnode-client> ");

            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+");
            String command = parts[0];

            try {
                switch (command) {

                    case "create-report":
                        clientService.createReportInteractive(sc);
                        break;

                    case "create-random":
                        clientService.createRandomReport();
                        break;

                    case "list-reports":
                        clientService.listReports();
                        break;

                    case "sync":
                        clientService.syncReports();
                        System.out.println();
                        break;

                    // case "reset-db": // DEBUGGING ONLY
                    //     clientService.resetDatabase();
                    //     break;

                    case "help":
                        printHelp();
                        break;

                    case "q":
                    case "exit":
                        System.out.println("Shutting down client.");
                        sc.close();
                        clientService.shutdown();
                        return;

                    default:
                        System.out.println("Unknown command: " + command);
                        printHelp();
                }

            } catch (Exception e) {
                System.err.println("Command failed: " + command + " - " + e.getMessage());
            }
        }
    }

    private static void printUsage() {
        System.out.println("Usage: java -jar deathnode-client.jar <node-id> [pseudonym]");
        System.out.println();
        System.out.println("  node-id:   Unique identifier for this node (e.g., '1.1.1.1')");
        System.out.println("  pseudonym: Optional pseudonym for this node (default: randomly generated)");
        System.out.println();
        System.out.println("Example:");
        System.out.println("  java -jar target/deathnode-client-1.0.0.jar 1.1.1.1 shadow_fox");
        System.out.println("  java -jar target/deathnode-client-1.0.0.jar 2.2.2.2");
        System.out.println();
    }

    private static void printHelp() {
        System.out.println();
        System.out.println("Available commands:");
        System.out.println("  create-report     Create a new report interactively");
        System.out.println("  create-random     Create random reports from a premade list");
        System.out.println("  list-reports      List all reports");
        System.out.println("  sync              Start synchronization with the server");
        // System.out.println("  reset-db          DEBUGGING ONLY: Reset the local database");
        System.out.println("  help              Show this help");
        System.out.println("  exit | q          Exit the client");
    }
}