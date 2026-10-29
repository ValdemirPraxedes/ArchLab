package br.com.archlab.simulation.core.v2;
public record ExchangeMetricV2(String exchangeId, String parentExchangeId,
        String callerComponentId, String calleeComponentId, String connectionId,
        long startMs, long endMs, long durationMs) {}
