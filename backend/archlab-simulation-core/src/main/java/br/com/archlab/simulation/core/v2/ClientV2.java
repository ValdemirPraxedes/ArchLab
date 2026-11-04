package br.com.archlab.simulation.core.v2;

public record ClientV2(String id, String displayName, ComponentTypeV2 type) implements ComponentV2 {
    public ClientV2(String id, String displayName) { this(id, displayName, ComponentTypeV2.CLIENT); }
    public ClientV2 {
        if (type == null || !type.isClient()) throw new IllegalArgumentException("Invalid client type");
    }
}
