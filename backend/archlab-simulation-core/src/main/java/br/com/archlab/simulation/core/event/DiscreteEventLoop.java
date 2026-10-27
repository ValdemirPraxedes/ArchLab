package br.com.archlab.simulation.core.event;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shared deterministic discrete-event dispatch loop for v1 and v2.
 * EventQueue owns time/sequence ordering; behavior callbacks may schedule further events.
 * A single safety ceiling is enforced regardless of protocol or model version.
 */
public final class DiscreteEventLoop {
    private DiscreteEventLoop() {}

    public static void run(BooleanSupplier hasEvents, Supplier<ScheduledEvent> next,
                           Consumer<ScheduledEvent> dispatch, long maxEvents) {
        Objects.requireNonNull(hasEvents, "hasEvents");
        Objects.requireNonNull(next, "next");
        Objects.requireNonNull(dispatch, "dispatch");
        if (maxEvents <= 0) throw new IllegalArgumentException("maxEvents must be positive");
        long count = 0;
        while (hasEvents.getAsBoolean()) {
            if (++count > maxEvents) throw new IllegalStateException("Event safety limit exceeded");
            dispatch.accept(Objects.requireNonNull(next.get(), "Missing scheduled event"));
        }
    }
}
