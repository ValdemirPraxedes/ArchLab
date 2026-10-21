package br.com.archlab.simulation.core.engine;

import br.com.archlab.simulation.core.domain.*;
import br.com.archlab.simulation.core.event.*;
import java.util.*;

/** Mutable state belonging exclusively to one execution. */
public final class SimulationContext {
    private final Architecture architecture;
    private final VirtualClock clock = new VirtualClock();
    private final EventQueue events = new EventQueue(clock);
    private final List<SimulationTraceEntry> trace = new ArrayList<>();
    private final Map<String, Component> components = new HashMap<>();
    private final Map<String, Connection> connections = new HashMap<>();
    private boolean completed;
    private String requestId;

    SimulationContext(Architecture architecture) {
        this.architecture = architecture;
        for (Component component : architecture.components()) components.put(component.id(), component);
        for (Connection connection : architecture.connections()) {
            connections.put(key(connection.sourceId(), connection.targetId()), connection);
        }
    }

    public long nowMs() { return clock.nowMs(); }
    public Architecture architecture() { return architecture; }
    public Component component(String id) { return Objects.requireNonNull(components.get(id), "Unknown component: " + id); }
    public String requestId() { return requestId; }
    public boolean completed() { return completed; }

    void start(String id) {
        if (requestId != null) throw new IllegalStateException("Execution already started");
        requestId = Objects.requireNonNull(id);
    }

    public void scheduleAfter(long delayMs, SimulationEvent event) {
        events.scheduleAfter(delayMs, event);
    }

    void record(ScheduledEvent event) {
        trace.add(new SimulationTraceEntry(event.timestampMs(), event.sequence(), event.event()));
    }

    ScheduledEvent next() { return events.pollNext().orElse(null); }
    boolean hasEvents() { return !events.isEmpty(); }

    public void complete(String id) {
        if (completed || !Objects.equals(requestId, id)) throw new IllegalStateException("Unexpected terminal event");
        completed = true;
    }

    /** Temporary logical hop scheduling until the dedicated transport implementation (MVP 05). */
    public void sendRequest(String sourceId, String targetId) {
        Connection connection = link(sourceId, targetId);
        scheduleAfter(connection.latencyMs(), new RequestArrived(requestId, sourceId, targetId));
    }

    public void sendResponse(String sourceId, String targetId) {
        Connection connection = link(targetId, sourceId);
        scheduleAfter(connection.latencyMs(), new ResponseArrived(requestId, sourceId, targetId));
    }

    private Connection link(String sourceId, String targetId) {
        Connection connection = connections.get(key(sourceId, targetId));
        if (connection == null) throw new IllegalStateException("Missing link: " + sourceId + " -> " + targetId);
        return connection;
    }

    private static String key(String sourceId, String targetId) { return sourceId + "\u0000" + targetId; }
    SimulationResult result() { return new SimulationResult(nowMs(), trace); }
}
