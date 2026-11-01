package br.com.archlab.simulation.core.v2;
/** -1 indicates an unfinished exchange; completion is upon caller receiving the response. */
public record ExchangeTraceV2(String exchangeId, String requestId, String parentExchangeId,
        String connectionId, String sourceId, String targetId, long startedAtMs, long completedAtMs) {}
