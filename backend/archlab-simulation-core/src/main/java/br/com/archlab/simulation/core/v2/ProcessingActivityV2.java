package br.com.archlab.simulation.core.v2;

public record ProcessingActivityV2(String id, String componentId, String requestId,
        String phase, long startMs, long endMs, long durationMs) implements TimelineActivityV2 {
    @Override public String kind() { return "PROCESSING"; }
}
