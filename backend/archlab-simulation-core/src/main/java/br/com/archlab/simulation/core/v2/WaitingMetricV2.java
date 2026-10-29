package br.com.archlab.simulation.core.v2;
public record WaitingMetricV2(String componentId, String exchangeId, long startMs, long endMs, long durationMs) {}
