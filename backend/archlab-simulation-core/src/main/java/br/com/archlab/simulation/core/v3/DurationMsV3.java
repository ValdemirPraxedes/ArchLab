package br.com.archlab.simulation.core.v3;
/** Nonnegative virtual milliseconds, with checked arithmetic. */
public record DurationMsV3(long value) implements Comparable<DurationMsV3> {
    public static final DurationMsV3 ZERO = new DurationMsV3(0);
    public DurationMsV3 {
        if (value < 0) throw new IllegalArgumentException("Duration must be nonnegative");
    }
    public DurationMsV3 plus(DurationMsV3 other) {
        return new DurationMsV3(Math.addExact(value, other.value));
    }
    @Override public int compareTo(DurationMsV3 other) { return Long.compare(value, other.value); }
}
