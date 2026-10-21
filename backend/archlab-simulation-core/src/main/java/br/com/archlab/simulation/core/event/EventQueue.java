package br.com.archlab.simulation.core.event;

import java.util.Objects;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * Deterministic, single-execution scheduling queue.
 *
 * Events may be enqueued while processing another event. Every event receives
 * a monotonically increasing sequence, including events at the current instant.
 * The engine controls the loop and terminal-state checks (MVP 04).
 */
public final class EventQueue {
    private final PriorityQueue<ScheduledEvent> events = new PriorityQueue<>();
    private final VirtualClock clock;
    private long nextSequence;

    public EventQueue(VirtualClock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ScheduledEvent scheduleAt(long timestampMs, SimulationEvent event) {
        Objects.requireNonNull(event, "event");
        if (timestampMs < clock.nowMs()) {
            throw new IllegalArgumentException("Cannot schedule an event in the past");
        }
        if (nextSequence == Long.MAX_VALUE) {
            throw new ArithmeticException("Event sequence exhausted");
        }
        ScheduledEvent scheduled = new ScheduledEvent(timestampMs, nextSequence++, event);
        events.add(scheduled);
        return scheduled;
    }

    public ScheduledEvent scheduleAfter(long durationMs, SimulationEvent event) {
        return scheduleAt(clock.after(durationMs), event);
    }

    /** Returns the next event and moves virtual time to its timestamp. */
    public Optional<ScheduledEvent> pollNext() {
        ScheduledEvent next = events.peek();
        if (next == null) return Optional.empty();
        // Also rejects events that have become retroactive due to external clock advancement.
        clock.advanceTo(next.timestampMs());
        return Optional.of(events.remove());
    }

    public int size() {
        return events.size();
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }
}
