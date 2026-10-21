package br.com.archlab.api.simulation;

import br.com.archlab.simulation.core.engine.*;
import br.com.archlab.simulation.core.event.*;
import br.com.archlab.simulation.core.transport.*;
import java.util.*;
import org.springframework.stereotype.Component;

/** Converts core results into the public OpenAPI structure. */
@Component
public class SimulationResponseMapper {
    public Map<String, Object> map(SimulationResult result) {
        LinkedHashMap<String, Object> response = new LinkedHashMap<>();
        response.put("execution", Map.of("id", "exec-001", "status", "COMPLETED",
                "totalDurationMs", result.totalDurationMs()));
        Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("components", result.initialState().components());
        Map<String, String> requests = new LinkedHashMap<>();
        result.finalState().requests().keySet().forEach(id -> requests.put(id, "CREATED"));
        initial.put("requests", requests);
        initial.put("exchanges", result.initialState().exchanges());
        response.put("initialState", initial);
        response.put("trace", trace(result));
        response.put("timeline", timeline(result));
        response.put("metrics", result.metrics());
        return response;
    }

    private List<Map<String, Object>> trace(SimulationResult result) {
        List<TimedTrace> traces = new ArrayList<>();
        long order = 0;
        for (StateTransitionTrace t : result.stateTransitions()) {
            Map<String, Object> entry = fields("type", "STATE_TRANSITION", "entityKind", t.entityType().name(),
                    "entityId", t.entityId(), "previousState", t.previousState(),
                    "newState", t.newState(), "cause", t.cause());
            traces.add(new TimedTrace(t.timestampMs(), order++, entry));
        }
        for (MessageTraceEntry t : result.messageTrace()) {
            SimulationMessage m = t.message();
            Map<String, Object> entry = fields("type", "MESSAGE_EVENT", "action", t.action().name(),
                    "messageId", m.id(), "requestId", m.requestId(), "exchangeId", m.exchangeId(),
                    "connectionId", t.connectionId(), "sourceId", m.sourceId(), "targetId", m.targetId(),
                    "direction", m.direction().name(), "intent", m.intent().name());
            traces.add(new TimedTrace(t.timestampMs(), order++, entry));
        }
        for (SimulationTraceEntry t : result.trace()) {
            SimulationEvent e = t.event();
            if (e instanceof ProcessingStarted p) {
                traces.add(new TimedTrace(t.timestampMs(), order++,
                        fields("type", "PROCESSING_EVENT", "action", "STARTED",
                                "componentId", p.componentId(), "requestId", p.requestId(),
                                "phase", phase(result, p.componentId(), t.timestampMs(), true))));
            } else if (e instanceof ProcessingCompleted p) {
                traces.add(new TimedTrace(t.timestampMs(), order++,
                        fields("type", "PROCESSING_EVENT", "action", "COMPLETED",
                                "componentId", p.componentId(), "requestId", p.requestId(),
                                "phase", phase(result, p.componentId(), t.timestampMs(), false))));
            }
        }
        traces.sort(Comparator.comparingLong(TimedTrace::time).thenComparingLong(TimedTrace::order));
        List<Map<String, Object>> output = new ArrayList<>();
        for (TimedTrace trace : traces) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("sequence", (long) output.size() + 1);
            row.put("timestampMs", trace.time());
            row.putAll(trace.fields());
            output.add(row);
        }
        return output;
    }

    private String phase(SimulationResult result, String componentId, long at, boolean start) {
        return result.timeline().stream()
                .filter(t -> t.kind() == TimelineActivity.Kind.PROCESSING
                        && t.entityId().equals(componentId)
                        && (start ? t.startMs() == at : t.endMs() == at)
                        && !t.phase().equals("API_POST_PROCESSING"))
                .map(TimelineActivity::phase).findFirst()
                .orElseThrow(() -> new IllegalStateException("No processing phase in timeline"));
    }

    private List<Map<String, Object>> timeline(SimulationResult result) {
        Map<Long, MessageTraceEntry> arrivalsByTime = new HashMap<>();
        for (MessageTraceEntry t : result.messageTrace())
            if (t.action() == MessageTraceEntry.Action.ARRIVED)
                arrivalsByTime.put(t.timestampMs(), t);
        List<Map<String, Object>> output = new ArrayList<>();
        for (TimelineActivity a : result.timeline()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", "activity-" + (output.size() + 1));
            row.put("kind", a.kind().name());
            if (a.kind() == TimelineActivity.Kind.PROCESSING) {
                row.put("componentId", a.entityId());
                row.put("requestId", "request-1");
                row.put("phase", a.phase());
            } else {
                MessageTraceEntry arrived = result.messageTrace().stream()
                        .filter(t -> t.action() == MessageTraceEntry.Action.ARRIVED
                                && t.connectionId().equals(a.entityId())
                                && t.timestampMs() == a.endMs())
                        .findFirst().orElseThrow();
                SimulationMessage m = arrived.message();
                row.put("messageId", m.id());
                row.put("connectionId", arrived.connectionId());
                row.put("sourceId", m.sourceId());
                row.put("targetId", m.targetId());
            }
            row.put("startMs", a.startMs());
            row.put("endMs", a.endMs());
            row.put("durationMs", a.durationMs());
            output.add(row);
        }
        return output;
    }

    private static Map<String, Object> fields(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) map.put((String) values[i], values[i + 1]);
        return map;
    }
    private record TimedTrace(long time, long order, Map<String, Object> fields) {}
}
