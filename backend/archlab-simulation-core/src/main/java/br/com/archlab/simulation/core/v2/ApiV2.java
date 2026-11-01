package br.com.archlab.simulation.core.v2;

public record ApiV2(String id, String displayName, long preProcessingMs,
                    long postProcessingMs) implements ComponentV2 {
    @Override public ComponentTypeV2 type() { return ComponentTypeV2.API; }
}
