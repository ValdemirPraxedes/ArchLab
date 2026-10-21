package br.com.archlab.simulation.core.event;

/**
 * Occurrences processed by the discrete-event simulation engine.
 * These are scheduling primitives, not trace entries.
 */
public sealed interface SimulationEvent permits RequestSent, RequestArrived,
        ProcessingStarted, ProcessingCompleted, ResponseSent, ResponseArrived,
        SimulationCompleted {
}
