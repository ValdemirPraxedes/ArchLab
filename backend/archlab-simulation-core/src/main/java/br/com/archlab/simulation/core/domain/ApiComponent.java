package br.com.archlab.simulation.core.domain;

public record ApiComponent(String id, long preProcessingMs, long postProcessingMs) implements Component {
    @Override public ComponentType type() { return ComponentType.API; }
}
