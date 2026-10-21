package br.com.archlab.simulation.core.engine;

import java.util.Objects;

/** Derived, immutable interval; the event trace remains the source of truth. */
public record TimelineActivity(Kind kind, String entityId, String phase,
                               long startMs, long endMs, long durationMs) {
    public enum Kind { COMMUNICATION, PROCESSING }
    public TimelineActivity {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(entityId);
        Objects.requireNonNull(phase);
        if (startMs < 0 || endMs < startMs || durationMs != Math.subtractExact(endMs, startMs))
            throw new IllegalArgumentException("Invalid timeline interval");
    }
}
