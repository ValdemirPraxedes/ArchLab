package br.com.archlab.simulation.core.engine;

import java.util.Objects;

/** A single transition in virtual time, independent from scheduled events. */
public record StateTransitionTrace(long timestampMs, long sequence, EntityType entityType,
                                   String entityId, String previousState, String newState, String cause) {
    public enum EntityType { COMPONENT, REQUEST, EXCHANGE }
    public StateTransitionTrace {
        if (timestampMs < 0 || sequence < 0) throw new IllegalArgumentException("Negative trace position");
        Objects.requireNonNull(entityType);
        Objects.requireNonNull(entityId);
        Objects.requireNonNull(previousState);
        Objects.requireNonNull(newState);
        Objects.requireNonNull(cause);
        if (previousState.equals(newState)) throw new IllegalArgumentException("No-op transition");
    }
}
