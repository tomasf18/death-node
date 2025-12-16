import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

public class Aggregator {

    private final int HISTORY_SIZE = 10;

    public static class NodeStats {
        public final LongAdder bytesOut = new LongAdder();
        public final LongAdder packets = new LongAdder();
    }

    private final List<ConcurrentHashMap<String, NodeStats>> history =
            new ArrayList<>(HISTORY_SIZE);

    /**
     * Called by sniffer
     */
    public void record(String srcIp, int bytes) {
        ConcurrentHashMap<String, NodeStats> currentWindow =
                history.getLast();
        NodeStats stats =
                currentWindow.computeIfAbsent(srcIp, ip -> new NodeStats());

        stats.bytesOut.add(bytes);
        stats.packets.increment();
    }

    /**
     * Called by decision loop (periodically)
     * Snapshots the last 10 seconds
     */
    public Map<String, NodeStats> snapshotAndReset() {
        Map<String, NodeStats> snapshot = new HashMap<>();

        for (ConcurrentHashMap<String, NodeStats> window : history) {
            for (Map.Entry<String, NodeStats> entry : window.entrySet()) {
                NodeStats acc =
                        snapshot.computeIfAbsent(
                                entry.getKey(),
                                ip -> new NodeStats()
                        );
                acc.bytesOut.add(entry.getValue().bytesOut.sum());
                acc.bytesOut.add(entry.getValue().packets.sum());
            }
        }

        while (history.size() >= HISTORY_SIZE) {
            history.removeFirst();
        }
        history.add(new ConcurrentHashMap<>());
        return snapshot;
    }

}
