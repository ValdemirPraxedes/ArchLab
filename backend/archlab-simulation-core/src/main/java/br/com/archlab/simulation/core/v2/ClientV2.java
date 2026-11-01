package br.com.archlab.simulation.core.v2;

public record ClientV2(String id, String displayName) implements ComponentV2 {
    @Override public ComponentTypeV2 type() { return ComponentTypeV2.CLIENT; }
}
