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
    private final ExecutionState state;
    private final ExecutionStateSnapshot initialState;
    private final List<SimulationTraceEntry> trace = new ArrayList<>();
    private long traceSequence;
    private final Map<String, Component> components = new HashMap<>();
    private boolean completed;
    private String requestId;
    private boolean apiWaiting;
    private long apiWaitStartedMs = -1;
    private long apiWaitingDurationMs;

    SimulationContext(Architecture architecture) {
        this.architecture = architecture;
        transport = new SimulationTransport(architecture);
        state = new ExecutionState(architecture);
        for (Component component : architecture.components()) components.put(component.id(), component);
        initialState = state.snapshot();
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
        state.createRequest(id);
    }

    public void scheduleAfter(long delayMs, SimulationEvent event) { events.scheduleAfter(delayMs, event); }

    void record(ScheduledEvent event) {
        trace.add(new SimulationTraceEntry(event.timestampMs(), traceSequence++, event.event()));
        SimulationEvent occurrence = event.event();
        if (occurrence instanceof RequestSent e && component(e.sourceId()) instanceof ClientComponent) {
            state.request(e.requestId(), RequestRuntimeState.CREATED, RequestRuntimeState.IN_PROGRESS, nowMs(), "client-request-sent");
            state.component(e.sourceId(), ComponentRuntimeState.IDLE, ComponentRuntimeState.WAITING, nowMs(), "client-request-sent");
        } else if (occurrence instanceof ProcessingStarted e) {
            ComponentRuntimeState current = state.component(e.componentId());
            if (current != ComponentRuntimeState.IDLE && current != ComponentRuntimeState.PROCESSING)
                throw new IllegalStateException("Unexpected processing start");
            if (current == ComponentRuntimeState.IDLE)
                state.component(e.componentId(), ComponentRuntimeState.IDLE, ComponentRuntimeState.PROCESSING, nowMs(), "processing-started");
        } else if (occurrence instanceof ResponseSent e && component(e.sourceId()) instanceof DatabaseComponent) {
            state.component(e.sourceId(), ComponentRuntimeState.PROCESSING, ComponentRuntimeState.COMPLETED, nowMs(), "database-response-sent");
        } else if (occurrence instanceof ResponseSent e && component(e.sourceId()) instanceof ApiComponent) {
            state.component(e.sourceId(), ComponentRuntimeState.PROCESSING, ComponentRuntimeState.COMPLETED, nowMs(), "api-response-sent");
        }
    }

    ScheduledEvent next() { return events.pollNext().orElse(null); }
    boolean hasEvents() { return !events.isEmpty(); }

    public void complete(String id) {
        if (completed || !Objects.equals(requestId, id) || !transport.completed(MessageIntent.API_REQUEST)
                || !transport.completed(MessageIntent.DATABASE_QUERY) || apiWaiting
                || state.request(id) != RequestRuntimeState.COMPLETED) {
            throw new IllegalStateException("Unexpected terminal event");
        }
        completed = true;
    }

    public void sendRequest(String sourceId, String targetId) {
        ensureActive();
        Component source = component(sourceId), target = component(targetId);
        MessageIntent intent;
        if (source instanceof ClientComponent && target instanceof ApiComponent) {
            intent = MessageIntent.API_REQUEST;
        } else if (source instanceof ApiComponent && target instanceof DatabaseComponent) {
            if (apiWaiting) throw new IllegalStateException("API is already waiting");
            intent = MessageIntent.DATABASE_QUERY;
        } else throw new IllegalStateException("Unsupported request direction");

        transport.sendRequest(requestId, sourceId, targetId, intent, clock, events);
        String exchangeId = exchangeId(intent);
        state.createExchange(exchangeId);
        state.exchange(exchangeId, ExchangeRuntimeState.CREATED, ExchangeRuntimeState.WAITING_RESPONSE, nowMs(), "request-sent");
        if (intent == MessageIntent.DATABASE_QUERY) {
            state.component(sourceId, ComponentRuntimeState.PROCESSING, ComponentRuntimeState.WAITING, nowMs(), "waiting-for-database");
            state.request(requestId, RequestRuntimeState.IN_PROGRESS, RequestRuntimeState.WAITING_DEPENDENCY, nowMs(), "waiting-for-database");
            apiWaiting = true;
            apiWaitStartedMs = nowMs();
        }
    }

    public void sendResponse(String sourceId, String targetId) {
        ensureActive();
        Component source = component(sourceId), target = component(targetId);
        MessageIntent intent;
        if (source instanceof DatabaseComponent && target instanceof ApiComponent) intent = MessageIntent.DATABASE_QUERY;
        else if (source instanceof ApiComponent && target instanceof ClientComponent) {
            if (apiWaiting) throw new IllegalStateException("Cannot return while API is waiting for DB");
            intent = MessageIntent.API_REQUEST;
        } else throw new IllegalStateException("Unsupported response direction");
        transport.sendResponse(requestId, sourceId, targetId, intent, clock, events);
    }

    void onArrival(SimulationEvent event) {
        SimulationMessage message = transport.arrive(event, nowMs());
        if (!requestId.equals(message.requestId())) throw new IllegalStateException("Mismatched request");
        if (message.direction() == MessageDirection.RESPONSE) {
            state.exchange(message.exchangeId(), ExchangeRuntimeState.WAITING_RESPONSE,
                    ExchangeRuntimeState.COMPLETED, nowMs(), "response-arrived");
            if (message.intent() == MessageIntent.DATABASE_QUERY) {
                if (!apiWaiting) throw new IllegalStateException("Unexpected DB response");
                apiWaitingDurationMs = Math.addExact(apiWaitingDurationMs,
                        Math.subtractExact(nowMs(), apiWaitStartedMs));
                apiWaiting = false;
                apiWaitStartedMs = -1;
                state.component(message.targetId(), ComponentRuntimeState.WAITING,
                        ComponentRuntimeState.PROCESSING, nowMs(), "database-response-arrived");
                state.request(requestId, RequestRuntimeState.WAITING_DEPENDENCY,
                        RequestRuntimeState.IN_PROGRESS, nowMs(), "database-response-arrived");
            } else {
                state.component(message.targetId(), ComponentRuntimeState.WAITING,
                        ComponentRuntimeState.COMPLETED, nowMs(), "api-response-arrived");
                state.request(requestId, RequestRuntimeState.IN_PROGRESS,
                        RequestRuntimeState.COMPLETED, nowMs(), "api-response-arrived");
            }
        }
    }

    private static String exchangeId(MessageIntent intent) {
        return intent == MessageIntent.API_REQUEST ? "exchange-api-1" : "exchange-db-1";
    }

    private void ensureActive() {
        if (requestId == null || completed) throw new IllegalStateException("Execution not active");
    }

    SimulationResult result() {
        TimelineProjector projector = new TimelineProjector();
        List<TimelineActivity> timeline = projector.project(architecture, trace, transport.trace());
        SimulationMetrics metrics = projector.metrics(timeline, apiWaitingDurationMs);
        return new SimulationResult(nowMs(), trace, transport.trace(), initialState,
                state.snapshot(), state.trace(), apiWaitingDurationMs, timeline, metrics);
    }
}
