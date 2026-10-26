package br.com.archlab.simulation.core.v2;

/** A configured synchronous outgoing dependency for one API; absence means direct reply. */
public record ApiExecutionV2(String componentId, String dependencyConnectionId) {}
