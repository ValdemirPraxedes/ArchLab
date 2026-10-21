package br.com.archlab.simulation.core.event;

/** Monotonic, per-execution virtual time in milliseconds; never uses a wall clock. */
public final class VirtualClock {
    private long nowMs;

    public long nowMs() {
        return nowMs;
    }

    public void advanceTo(long timestampMs) {
        if (timestampMs < nowMs) {
            throw new IllegalArgumentException("Virtual time cannot move backwards");
        }
        nowMs = timestampMs;
    }

    public long after(long durationMs) {
        if (durationMs < 0) throw new IllegalArgumentException("Duration must be nonnegative");
        return Math.addExact(nowMs, durationMs);
    }
}
