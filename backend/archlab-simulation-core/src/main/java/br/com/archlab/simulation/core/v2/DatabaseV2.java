package br.com.archlab.simulation.core.v2;

public record DatabaseV2(String id, String displayName, long processingMs) implements ComponentV2 {
    @Override public ComponentTypeV2 type() { return ComponentTypeV2.DATABASE; }
}
