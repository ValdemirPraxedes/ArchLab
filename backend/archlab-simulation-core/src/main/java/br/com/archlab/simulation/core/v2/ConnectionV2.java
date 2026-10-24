package br.com.archlab.simulation.core.v2;

public record ConnectionV2(String id, String sourceId, String targetId,
                           long latencyMs, IntegrationV2 integration) {}
