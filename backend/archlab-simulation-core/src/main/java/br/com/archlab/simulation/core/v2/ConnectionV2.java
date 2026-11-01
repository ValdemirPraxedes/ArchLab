package br.com.archlab.simulation.core.v2;

/** A connection may override the destination API's processing times atomically. */
public record ConnectionV2(String id, String sourceId, String targetId,
        long latencyMs, IntegrationV2 integration, ApiProcessingOverrideV2 apiProcessingOverride) {
    /** Keeps all existing v2 construction sites source-compatible. */
    public ConnectionV2(String id, String sourceId, String targetId,
            long latencyMs, IntegrationV2 integration) {
        this(id, sourceId, targetId, latencyMs, integration, null);
    }
}
