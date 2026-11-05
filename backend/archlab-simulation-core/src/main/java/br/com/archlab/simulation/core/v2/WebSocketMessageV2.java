package br.com.archlab.simulation.core.v2;

public record WebSocketMessageV2(String sender, long intervalMs, String payload) {}
