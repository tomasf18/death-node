package com.deathnode.monitor;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Main {

    private static final long MAX_BYTES = 0;
    private static final long MAX_PACKETS = 0;
    private static final double MAX_SHARE = 0.35;
    private static final long BLOCK_DURATION_MS = 5_000;

    public static void main(String[] args) {

        String i1 = "eth1";
        String i2 = "eth2";

        Aggregator aggregator = new Aggregator();

        Sniffer sniffer = new Sniffer(i1, aggregator);
        Sniffer sniffer2 = new Sniffer(i2, aggregator);

        new Thread(sniffer).start();
        //new Thread(sniffer2).start();

        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();

        /**
         * Decision loop: every second, check activity for the last 10 seconds.
         */
        scheduler.scheduleAtFixedRate(() -> {
            Map<String, Aggregator.NodeStats> snapshot = aggregator.snapshotAndReset();

            if (snapshot.isEmpty()) {
                System.out.println("No snapshot found");
                return;
            }

            // --- Limpar consola ---
            System.out.print("\033[2J\033[H\033[3J"); // Clear screen + clear scrollback
            System.out.flush();

            long totalBytes = snapshot.values().stream()
                    .mapToLong(s -> s.bytesOut.sum())
                    .sum();

            // --- Print table header ---
            System.out.println("------------------------------------------------------------");
            System.out.printf("%-15s | %-10s | %-10s | %-7s%n", "SRC IP", "BYTES", "PACKETS", "SHARE");
            System.out.println("------------------------------------------------------------");

            for (Map.Entry<String, Aggregator.NodeStats> entry : snapshot.entrySet()) {

                String ip = entry.getKey();
                long bytes = entry.getValue().bytesOut.sum();
                long packets = entry.getValue().packets.sum();
                double share = totalBytes == 0 ? 0.0 : (double) bytes / totalBytes;

                // Print table row
                System.out.printf("%-15s | %-10d | %-10d | %-6.1f%%%n",
                        ip, bytes, packets, share * 100);

            }

            System.out.println("------------------------------------------------------------");

        }, 1, 10, TimeUnit.SECONDS);


        System.out.println("[Monitor] Decision loop running");

    }
}
