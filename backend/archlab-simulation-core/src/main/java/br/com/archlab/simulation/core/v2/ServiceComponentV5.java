package br.com.archlab.simulation.core.v2;

import java.util.List;
import java.util.Map;

/** v0.5 component metadata; behavior execution remains in the shared simulation engine. */
public record ServiceComponentV5(String id, String displayName, ComponentTypeV2 type,
                                 long preProcessingMs, long postProcessingMs,
                                 long defaultTtlMs, List<Schedule> schedules,
                                 List<Route> routes, List<Rule> rules,
                                 String defaultDecision) implements ComponentV2 {
    public record Schedule(String id, long firstAtMs, long intervalMs, int occurrenceCount, String behaviorId) {}
    public record Route(String operation, String connectionId) {}
    public record Rule(String id, String field, String operator, Object expectedValue,
                       int priority, String decision) {}

    public ServiceComponentV5 {
        if (type == null || (!type.isProcessor() && !type.isVirtualResource()) || type == ComponentTypeV2.API)
            throw new IllegalArgumentException("Invalid v0.5 component type");
        schedules = List.copyOf(schedules);
        routes = List.copyOf(routes);
        rules = List.copyOf(rules);
    }
}
