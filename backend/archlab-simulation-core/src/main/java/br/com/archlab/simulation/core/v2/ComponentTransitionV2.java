package br.com.archlab.simulation.core.v2;
public record ComponentTransitionV2(String componentId, ComponentRuntimeStateV2 from,
        ComponentRuntimeStateV2 to, long timestampMs) {}
