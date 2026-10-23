package br.com.archlab.simulation.core.domain;

public record ClientComponent(String id) implements Component {
    @Override public ComponentType type() { return ComponentType.CLIENT; }
}
