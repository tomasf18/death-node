package com.deathnode.client;

import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import com.deathnode.client.service.ClientService;
import com.deathnode.client.service.DatabaseService;

public class Main {

    private static final int PORT = 9090;

    public static void main(String[] args) throws Exception {

        DatabaseService db = new DatabaseService();
        ClientService clientService = new ClientService(db);
        
        Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);

        System.out.println("DeathNode client started.");
        System.out.println("Type 'help' to see available commands.");

        while (true) {
            System.out.print("deathnode-client> ");

            String line = sc.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+");
            String command = parts[0];

            try {
                switch (command) {

                    case "create-report":
                        clientService.createReportInteractive(sc);
                        break;

                    case "list-reports":
                        clientService.listReports();
                        break;

                    case "help":
                        printHelp();
                        break;

                    case "q":
                    case "exit":
                        System.out.println("Shutting down client.");
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

    private static void printHelp() {
        System.out.println();
        System.out.println("Available commands:");
        System.out.println("  create-report     Create a new report interactively");
        System.out.println("  create-random     Create random reports from a premade list");
        System.out.println("  list-reports      List all reports");
        System.out.println("  help              Show this help");
        System.out.println("  exit | q          Exit the client");
        System.out.println();
    }
}