package br.com.archlab.simulation.core.engine;

import java.util.Map;

/** Immutable snapshot of each entity family. */
public record ExecutionStateSnapshot(Map<String, ComponentRuntimeState> components,
                                     Map<String, RequestRuntimeState> requests,
                                     Map<String, ExchangeRuntimeState> exchanges) {
    public ExecutionStateSnapshot {
        components = Map.copyOf(components);
        requests = Map.copyOf(requests);
        exchanges = Map.copyOf(exchanges);
    }
}
