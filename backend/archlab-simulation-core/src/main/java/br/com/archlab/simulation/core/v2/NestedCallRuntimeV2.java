package br.com.archlab.simulation.core.v2;

import br.com.archlab.simulation.core.engine.SimulationTraceEntry;
import br.com.archlab.simulation.core.event.*;
import java.util.*;

/**
 * BE-05 v2 flow coordinator, reusing the existing EventQueue, VirtualClock
 * and event vocabulary, via SimulationEngine.execute(v2, scenario).
 * No threads, network requests, real SQL or wall clock.
 *
 * BE-06 will enrich these events with hierarchical message/exchange identity.
 */
public final class NestedCallRuntimeV2 {
    private static final String REQUEST_ID = "request-1";
    private static final int MAX_EVENTS = 100_000;
    private final ScenarioValidatorV2 validator = new ScenarioValidatorV2();

    public NestedExecutionResultV2 run(ArchitectureV2 architecture, ScenarioV2 scenario) {
        var validation = validator.validate(architecture, scenario);
        if (!validation.valid()) throw new IllegalArgumentException("Invalid architecture/scenario: " + validation.violations());
        VirtualClock clock = new VirtualClock();
        EventQueue queue = new EventQueue(clock);
        Map<String, ComponentV2> components = new HashMap<>();
        for (ComponentV2 c : architecture.components()) components.put(c.id(), c);
        Map<String, ConnectionV2> connections = new HashMap<>();
        Map<String, ConnectionV2> edges = new HashMap<>();
        for (ConnectionV2 c : architecture.connections()) {
            connections.put(c.id(), c);
            edges.put(key(c.sourceId(), c.targetId()), c);
        }
        Map<String, String> dependencies = new HashMap<>();
        for (ApiExecutionV2 choice : scenario.apiExecutions()) {
            dependencies.put(choice.componentId(), choice.dependencyConnectionId());
        }
        Map<String, Stage> stages = new HashMap<>();
        Map<String, String> callers = new HashMap<>();
        List<SimulationTraceEntry> trace = new ArrayList<>();
        List<ExchangeTraceV2> exchanges = new ArrayList<>();
        List<CorrelatedMessageV2> messages = new ArrayList<>();
        List<ComponentTransitionV2> transitions = new ArrayList<>();
        Map<String, String> activeByTarget = new HashMap<>();
        Map<String, String> pendingByEdge = new HashMap<>();
        Map<String, ComponentRuntimeStateV2> states = new HashMap<>();
        for (ComponentV2 component : architecture.components())
            states.put(component.id(), ComponentRuntimeStateV2.IDLE);
        long[] exchangeCounter = {0};
        long[] messageCounter = {0};
        ConnectionV2 entry = connections.get(scenario.entryConnectionId());
        queue.scheduleAfter(0, new RequestSent(REQUEST_ID, entry.sourceId(), entry.targetId()));
        boolean[] completed = {false};
        DiscreteEventLoop.run(() -> !queue.isEmpty(), () -> queue.pollNext().orElseThrow(), scheduled -> {
            SimulationEvent event = scheduled.event();
            trace.add(new SimulationTraceEntry(scheduled.timestampMs(), scheduled.sequence(), event));

            if (event instanceof RequestSent e) {
                if (!edges.containsKey(key(e.sourceId(), e.targetId())))
                    throw new IllegalStateException("Missing outgoing edge");
                if (!(components.get(e.sourceId()) instanceof ClientV2) &&
                        stages.get(e.sourceId()) != Stage.WAITING)
                    throw new IllegalStateException("API must wait for its downstream dependency");
                callers.put(e.targetId(), e.sourceId());
                String id = "exchange-" + (++exchangeCounter[0]);
                String parent = activeByTarget.get(e.sourceId());
                exchanges.add(new ExchangeTraceV2(id, REQUEST_ID, parent,
                        edges.get(key(e.sourceId(), e.targetId())).id(),
                        e.sourceId(), e.targetId(), clock.nowMs(), -1));
                activeByTarget.put(e.targetId(), id);
                pendingByEdge.put(key(e.sourceId(), e.targetId()), id);
                messages.add(new CorrelatedMessageV2("message-" + (++messageCounter[0]),
                        REQUEST_ID, id, e.sourceId(), e.targetId(), "REQUEST", "SENT", clock.nowMs()));
                if (components.get(e.sourceId()) instanceof ClientV2)
                    state(states, transitions, e.sourceId(), ComponentRuntimeStateV2.WAITING, clock.nowMs());
                queue.scheduleAfter(edges.get(key(e.sourceId(), e.targetId())).latencyMs(),
                        new RequestArrived(REQUEST_ID, e.sourceId(), e.targetId()));
            } else if (event instanceof RequestArrived e) {
                String id = pendingByEdge.get(key(e.sourceId(), e.targetId()));
                if (id == null) throw new IllegalStateException("Unexpected request arrival");
                messages.add(new CorrelatedMessageV2("message-" + messageCounter[0],
                        REQUEST_ID, id, e.sourceId(), e.targetId(), "REQUEST", "ARRIVED", clock.nowMs()));
                state(states, transitions, e.targetId(), ComponentRuntimeStateV2.PROCESSING, clock.nowMs());
                if (components.get(e.targetId()) instanceof ApiV2) {
                    if (stages.putIfAbsent(e.targetId(), Stage.PRE_READY) != null)
                        throw new IllegalStateException("Unexpected repeated API invocation");
                    queue.scheduleAfter(0, new ProcessingStarted(REQUEST_ID, e.targetId()));
                } else if (components.get(e.targetId()) instanceof DatabaseV2) {
                    queue.scheduleAfter(0, new ProcessingStarted(REQUEST_ID, e.targetId()));
                } else throw new IllegalStateException("Invalid request recipient");
            } else if (event instanceof ProcessingStarted e) {
                ComponentV2 component = components.get(e.componentId());
                if (component instanceof ApiV2 api) {
                    Stage stage = stages.get(e.componentId());
                    if (stage == Stage.PRE_READY) {
                        stages.put(e.componentId(), Stage.PRE_RUNNING);
                        queue.scheduleAfter(api.preProcessingMs(), new ProcessingCompleted(REQUEST_ID, e.componentId()));
                    } else if (stage == Stage.POST_READY) {
                        stages.put(e.componentId(), Stage.POST_RUNNING);
                        queue.scheduleAfter(api.postProcessingMs(), new ProcessingCompleted(REQUEST_ID, e.componentId()));
                    } else throw new IllegalStateException("Unexpected API processing stage: " + stage);
                } else if (component instanceof DatabaseV2 database) {
                    queue.scheduleAfter(database.processingMs(), new ProcessingCompleted(REQUEST_ID, e.componentId()));
                } else throw new IllegalStateException("Unexpected processing component");
            } else if (event instanceof ProcessingCompleted e) {
                ComponentV2 component = components.get(e.componentId());
                if (component instanceof DatabaseV2) {
                    queue.scheduleAfter(0, new ResponseSent(REQUEST_ID, e.componentId(), callers.get(e.componentId())));
                } else if (component instanceof ApiV2) {
                    Stage stage = stages.get(e.componentId());
                    if (stage == Stage.PRE_RUNNING) {
                        String dependencyId = dependencies.get(e.componentId());
                        if (dependencyId == null) {
                            stages.put(e.componentId(), Stage.POST_READY);
                            queue.scheduleAfter(0, new ProcessingStarted(REQUEST_ID, e.componentId()));
                        } else {
                            stages.put(e.componentId(), Stage.WAITING);
                            state(states, transitions, e.componentId(),
                                    ComponentRuntimeStateV2.WAITING, clock.nowMs());
                            ConnectionV2 dependency = connections.get(dependencyId);
                            queue.scheduleAfter(0, new RequestSent(REQUEST_ID, e.componentId(), dependency.targetId()));
                        }
                    } else if (stage == Stage.POST_RUNNING) {
                        stages.put(e.componentId(), Stage.DONE);
                        state(states, transitions, e.componentId(),
                                ComponentRuntimeStateV2.COMPLETED, clock.nowMs());
                        queue.scheduleAfter(0, new ResponseSent(REQUEST_ID, e.componentId(), callers.get(e.componentId())));
                    } else throw new IllegalStateException("Unexpected API completion stage");
                }
            } else if (event instanceof ResponseSent e) {
                String id = pendingByEdge.get(key(e.targetId(), e.sourceId()));
                if (id == null) throw new IllegalStateException("Unexpected response sent");
                messages.add(new CorrelatedMessageV2("message-" + (++messageCounter[0]),
                        REQUEST_ID, id, e.sourceId(), e.targetId(), "RESPONSE", "SENT", clock.nowMs()));
                if (components.get(e.sourceId()) instanceof DatabaseV2)
                    state(states, transitions, e.sourceId(), ComponentRuntimeStateV2.COMPLETED, clock.nowMs());
                ConnectionV2 edge = edges.get(key(e.targetId(), e.sourceId()));
                if (edge == null) throw new IllegalStateException("Response has no reverse route");
                queue.scheduleAfter(edge.latencyMs(), new ResponseArrived(REQUEST_ID, e.sourceId(), e.targetId()));
            } else if (event instanceof ResponseArrived e) {
                String id = pendingByEdge.remove(key(e.targetId(), e.sourceId()));
                if (id == null) throw new IllegalStateException("Duplicate or unexpected response");
                if (!Objects.equals(activeByTarget.remove(e.sourceId()), id))
                    throw new IllegalStateException("Response has wrong active exchange");
                int index = -1;
                for (int i = 0; i < exchanges.size(); i++)
                    if (exchanges.get(i).exchangeId().equals(id)) { index = i; break; }
                if (index < 0 || exchanges.get(index).completedAtMs() >= 0)
                    throw new IllegalStateException("Duplicate exchange completion");
                var original = exchanges.get(index);
                exchanges.set(index, new ExchangeTraceV2(id, REQUEST_ID, original.parentExchangeId(),
                        original.connectionId(), original.sourceId(), original.targetId(),
                        original.startedAtMs(), clock.nowMs()));
                messages.add(new CorrelatedMessageV2("message-" + messageCounter[0],
                        REQUEST_ID, id, e.sourceId(), e.targetId(), "RESPONSE", "ARRIVED", clock.nowMs()));
                ComponentV2 recipient = components.get(e.targetId());
                if (recipient instanceof ClientV2) {
                    if (!e.targetId().equals(scenario.entryComponentId()))
                        throw new IllegalStateException("Response delivered to different client");
                    state(states, transitions, e.targetId(), ComponentRuntimeStateV2.COMPLETED, clock.nowMs());
                    completed[0] = true;
                    if (!queue.isEmpty()) throw new IllegalStateException("Residual events after completion");
                } else if (recipient instanceof ApiV2) {
                    if (stages.get(e.targetId()) != Stage.WAITING
                            || !Objects.equals(connections.get(dependencies.get(e.targetId())).targetId(), e.sourceId()))
                        throw new IllegalStateException("Unexpected response to API");
                    state(states, transitions, e.targetId(), ComponentRuntimeStateV2.PROCESSING, clock.nowMs());
                    stages.put(e.targetId(), Stage.POST_READY);
                    queue.scheduleAfter(0, new ProcessingStarted(REQUEST_ID, e.targetId()));
                } else throw new IllegalStateException("Invalid response recipient");
            } else {
                throw new IllegalStateException("Unexpected event: " + event);
            }
        }, MAX_EVENTS);
        if (!completed[0]) throw new IllegalStateException("Event queue exhausted before client response");
        if (!pendingByEdge.isEmpty() || exchanges.stream().anyMatch(e -> e.completedAtMs() < 0))
            throw new IllegalStateException("Unfinished exchanges");
        return new NestedExecutionResultV2(clock.nowMs(), REQUEST_ID, trace, exchanges, messages, transitions);
    }

    private static void state(Map<String, ComponentRuntimeStateV2> states,
            List<ComponentTransitionV2> transitions, String componentId,
            ComponentRuntimeStateV2 target, long timeMs) {
        ComponentRuntimeStateV2 current = states.get(componentId);
        if (current == null || current == target) throw new IllegalStateException("Invalid state transition");
        states.put(componentId, target);
        transitions.add(new ComponentTransitionV2(componentId, current, target, timeMs));
    }

    private static String key(String source, String target) {
        return source.length() + ":" + source + target.length() + ":" + target;
    }

    private enum Stage { PRE_READY, PRE_RUNNING, WAITING, POST_READY, POST_RUNNING, DONE }
}
