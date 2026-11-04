package br.com.archlab.simulation.core.v4;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;

/** Tracks the main operation separately from virtual resources and derived work. */
public final class ExecutionLifecycleV4 {
    public enum MainStatus { COMPLETED, FAILED, TIMED_OUT }
    public enum GlobalStatus { COMPLETED, FAILED, TIMED_OUT, COMPLETED_WITH_ERRORS }
    public enum ResourceKind { WEBSOCKET_SESSION, BROKER_MESSAGE, DELIVERY }
    public enum ResourceStatus { ACTIVE, COMPLETED, FAILED, TIMED_OUT }
    public record Resource(String id, ResourceKind kind, ResourceStatus status) {}

    private static final long MAX_DURATION_MS = 3_600_000L;
    private static final int MAX_EVENTS = 50_000;
    private final Map<String, Resource> resources = new LinkedHashMap<>();
    private MainStatus mainStatus;
    private long lastTimeMs;
    private int eventCount;

    public void eventAt(long timeMs) {
        if (timeMs < lastTimeMs || timeMs > MAX_DURATION_MS) throw new IllegalArgumentException("Virtual duration limit exceeded");
        if (eventCount >= MAX_EVENTS) throw new IllegalArgumentException("Event limit exceeded");
        eventCount++;
        lastTimeMs = timeMs;
    }

    public void open(String id, ResourceKind kind) {
        if (id == null || id.isBlank() || kind == null || resources.putIfAbsent(id,
                new Resource(id, kind, ResourceStatus.ACTIVE)) != null)
            throw new IllegalArgumentException("Invalid or duplicate resource");
    }

    public void finish(String id, ResourceStatus status) {
        Resource current = resources.get(id);
        if (current == null || current.status() != ResourceStatus.ACTIVE || status == null
                || status == ResourceStatus.ACTIVE) throw new IllegalArgumentException("Invalid resource transition");
        resources.put(id, new Resource(id, current.kind(), status));
    }

    public void finishMain(MainStatus status) {
        if (mainStatus != null || status == null) throw new IllegalArgumentException("Invalid main transition");
        mainStatus = status;
    }

    public MainStatus mainStatus() { return mainStatus; }
    public long totalDurationMs() { return lastTimeMs; }
    public int eventCount() { return eventCount; }
    public Map<String, Resource> resources() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(resources));
    }

    /** Null means the global execution remains active. */
    public GlobalStatus globalStatus() {
        if (mainStatus == null || resources.values().stream().anyMatch(r -> r.status() == ResourceStatus.ACTIVE))
            return null;
        if (mainStatus == MainStatus.FAILED) return GlobalStatus.FAILED;
        if (mainStatus == MainStatus.TIMED_OUT) return GlobalStatus.TIMED_OUT;
        return resources.values().stream().anyMatch(r -> r.status() != ResourceStatus.COMPLETED)
                ? GlobalStatus.COMPLETED_WITH_ERRORS : GlobalStatus.COMPLETED;
    }
}
