package br.com.archlab.simulation.core.domain;

public record Connection(String id, String sourceId, String targetId, Protocol protocol, long latencyMs) {}
