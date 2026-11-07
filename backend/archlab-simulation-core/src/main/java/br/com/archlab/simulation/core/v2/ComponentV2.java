package br.com.archlab.simulation.core.v2;

/** Independent v2 domain model; does not change legacy v1 components. */
public sealed interface ComponentV2 permits ClientV2, ApiV2, DatabaseV2, BrokerV2, ServiceComponentV5, DataResourceV6, DataProcessorV6 {
    String id();
    String displayName();
    ComponentTypeV2 type();
}
