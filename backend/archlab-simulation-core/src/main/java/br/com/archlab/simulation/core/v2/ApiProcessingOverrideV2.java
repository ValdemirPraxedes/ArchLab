package br.com.archlab.simulation.core.v2;

/** Effective processing times for the destination API, not for the transport. */
public record ApiProcessingOverrideV2(long preProcessingMs, long postProcessingMs) {}
