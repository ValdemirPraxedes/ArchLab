package br.com.archlab.simulation.core.engine;

import br.com.archlab.simulation.core.domain.*;
import br.com.archlab.simulation.core.event.*;
import br.com.archlab.simulation.core.transport.*;
import java.util.*;

/** Mutable state belonging exclusively to one execution. */
public final class SimulationContext {
    private final Architecture architecture;
    private final VirtualClock clock = new VirtualClock();
    private final EventQueue events = new EventQueue(clock);
    private final SimulationTransport transport;
    private final List<SimulationTraceEntry> trace = new ArrayList<>();
    private long traceSequence;
    private final Map<String, Component> components = new HashMap<>();
    private boolean completed;
    private String requestId;
    private boolean apiWaiting;

    SimulationContext(Architecture architecture) {
        this.architecture = architecture;
        transport = new SimulationTransport(architecture);
        for (Component component : architecture.components()) components.put(component.id(), component);
    }

    public long nowMs() { return clock.nowMs(); }
    public Architecture architecture() { return architecture; }
    public Component component(String id) { return Objects.requireNonNull(components.get(id), "Unknown component: " + id); }
    public String requestId() { return requestId; }
    public boolean completed() { return completed; }
    public boolean apiWaiting() { return apiWaiting; }

    void start(String id) {
        if (requestId != null) throw new IllegalStateException("Execution already started");
        requestId = Objects.requireNonNull(id);
    }

    public void scheduleAfter(long delayMs, SimulationEvent event) {
        events.scheduleAfter(delayMs, event);
    }

    void record(ScheduledEvent event) {
        trace.add(new SimulationTraceEntry(event.timestampMs(), traceSequence++, event.event()));
    }

    ScheduledEvent next() { return events.pollNext().orElse(null); }
    boolean hasEvents() { return !events.isEmpty(); }

    public void complete(String id) {
        if (completed || !Objects.equals(requestId, id) || !transport.completed(MessageIntent.API_REQUEST)
                || !transport.completed(MessageIntent.DATABASE_QUERY) || apiWaiting) {
            throw new IllegalStateException("Unexpected terminal event");
        }
        completed = true;
    }

    public void sendRequest(String sourceId, String targetId) {
        ensureActive();
        Component source = component(sourceId);
        Component target = component(targetId);
        MessageIntent intent;
        if (source instanceof ClientComponent && target instanceof ApiComponent) {
            intent = MessageIntent.API_REQUEST;
        } else if (source instanceof ApiComponent && target instanceof DatabaseComponent) {
            if (apiWaiting) throw new IllegalStateException("API is already waiting");
            intent = MessageIntent.DATABASE_QUERY;
            apiWaiting = true;
        } else {
            throw new IllegalStateException("Unsupported request direction");
        }
        transport.sendRequest(requestId, sourceId, targetId, intent, clock, events);
    }

    public void sendResponse(String sourceId, String targetId) {
        ensureActive();
        Component source = component(sourceId);
        Component target = component(targetId);
        MessageIntent intent;
        if (source instanceof DatabaseComponent && target instanceof ApiComponent) {
            intent = MessageIntent.DATABASE_QUERY;
        } else if (source instanceof ApiComponent && target instanceof ClientComponent) {
            if (apiWaiting) throw new IllegalStateException("Cannot return while API is waiting for DB");
            intent = MessageIntent.API_REQUEST;
        } else {
            throw new IllegalStateException("Unsupported response direction");
        }
        transport.sendResponse(requestId, sourceId, targetId, intent, clock, events);
    }

    void onArrival(SimulationEvent event) {
        SimulationMessage message = transport.arrive(event, nowMs());
        if (!requestId.equals(message.requestId())) throw new IllegalStateException("Mismatched request");
        if (message.direction() == MessageDirection.RESPONSE && message.intent() == MessageIntent.DATABASE_QUERY) {
            if (!apiWaiting) throw new IllegalStateException("Unexpected DB response");
            apiWaiting = false;
        }
    }

    private void ensureActive() {
        if (requestId == null || completed) throw new IllegalStateException("Execution not active");
    }

    SimulationResult result() { return new SimulationResult(nowMs(), trace, transport.trace()); }
}
