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

        String interfaceName = "eth0";

        // TODO saber config da rede... quantas interfaces?

        Aggregator aggregator = new Aggregator();
        Sniffer sniffer = new Sniffer(interfaceName, aggregator);
        new Thread(sniffer).start();

        ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();

        /**
         * Decision loop: every second, check activity for the last 10 seconds.
         */
        scheduler.scheduleAtFixedRate(() -> {

            Map<String, Aggregator.NodeStats> snapshot =
                    aggregator.snapshotAndReset();

            if (snapshot.isEmpty()) {
                return;
            }

            long totalBytes = snapshot.values().stream()
                    .mapToLong(s -> s.bytesOut.sum())
                    .sum();

            for (Map.Entry<String, Aggregator.NodeStats> entry
                    : snapshot.entrySet()) {

                String ip = entry.getKey();
                long bytes = entry.getValue().bytesOut.sum();
                long packets = entry.getValue().packets.sum();

                double share = (double) bytes / totalBytes;

                // --- Decision logic ---
                if (bytes > MAX_BYTES || share > MAX_SHARE || packets > MAX_PACKETS) {

                    System.out.printf(
                            "[Decision] %s abusive: %d bytes/s (%.1f%%)%n",
                            ip, bytes, share * 100
                    );

                    // TODO enforce action against abuser
                }
            }

        }, 1, 1, TimeUnit.SECONDS);

        System.out.println("[Monitor] Decision loop running");

    }
}
