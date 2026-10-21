package br.com.archlab.simulation.core.event;

import java.util.Objects;

/** Timestamp and insertion sequence define a stable total ordering for queued events. */
public record ScheduledEvent(long timestampMs, long sequence, SimulationEvent event)
        implements Comparable<ScheduledEvent> {
    public ScheduledEvent {
        if (timestampMs < 0) throw new IllegalArgumentException("timestampMs must be nonnegative");
        if (sequence < 0) throw new IllegalArgumentException("sequence must be nonnegative");
        Objects.requireNonNull(event, "event");
    }

    @Override
    public int compareTo(ScheduledEvent other) {
        int byTime = Long.compare(timestampMs, other.timestampMs);
        return byTime != 0 ? byTime : Long.compare(sequence, other.sequence);
    }
}
