package br.com.archlab.simulation.core.v2;
/** SENT and ARRIVED use the same logical message ID. */
public record CorrelatedMessageV2(String messageId, String requestId, String exchangeId,
        String sourceId, String targetId, String direction, String phase, long timestampMs) {}
