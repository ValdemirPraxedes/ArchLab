package br.com.archlab.simulation.core.engine;

/** Waiting overlaps DB and network work and is not added to total duration. */
public record SimulationMetrics(long apiPreProcessingMs, long apiPostProcessingMs,
                                long databaseProcessingMs, long communicationMs,
                                long apiWaitingMs) {
    public SimulationMetrics {
        if (apiPreProcessingMs < 0 || apiPostProcessingMs < 0 || databaseProcessingMs < 0
                || communicationMs < 0 || apiWaitingMs < 0) {
            throw new IllegalArgumentException("Negative metric");
        }
    }
    public long nonOverlappingTotalMs() {
        return Math.addExact(Math.addExact(apiPreProcessingMs, apiPostProcessingMs),
                Math.addExact(databaseProcessingMs, communicationMs));
    }
}
