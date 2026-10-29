package br.com.archlab.simulation.core.v2;

public record CommunicationActivityV2(String id, String messageId, String exchangeId,
        String connectionId, String sourceId, String targetId, long startMs, long endMs,
        long durationMs) implements TimelineActivityV2 {
    @Override public String kind() { return "COMMUNICATION"; }
}
