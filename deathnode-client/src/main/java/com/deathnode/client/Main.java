package com.deathnode.client;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: java -jar client.jar <command> [args]");
            System.out.println("Commands: create-report <signerId> <seq> | list | sync <nodeId>");
            return;
        }
        LocalDb db = new LocalDb();
        ClientService svc = new ClientService(db);

        switch (args[0]) {
            case "create-report":
                if (args.length < 3) { System.out.println("usage create-report <signerId> <seq>"); break; }
                svc.createReport(args[1], Long.parseLong(args[2]));
                break;
            case "list":
                svc.list();
                break;
            case "sync":
                if (args.length < 2) { System.out.println("usage sync <nodeId>"); break; }
                svc.sync(args[1]);
                break;
            default:
                System.out.println("unknown command");
        }
    }
}
