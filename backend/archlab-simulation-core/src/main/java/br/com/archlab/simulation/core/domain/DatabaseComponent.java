package br.com.archlab.simulation.core.domain;

public record DatabaseComponent(String id, long processingMs) implements Component {
    @Override public ComponentType type() { return ComponentType.DATABASE; }
}
