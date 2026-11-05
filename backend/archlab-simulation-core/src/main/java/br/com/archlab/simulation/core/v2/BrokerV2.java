package br.com.archlab.simulation.core.v2;

import java.util.List;

/** In-memory logical destinations and subscriptions; no external broker is created. */
public record BrokerV2(String id, String displayName,
                       List<BrokerDestinationV2> destinations,
                       List<BrokerSubscriptionV2> subscriptions) implements ComponentV2 {
    public BrokerV2 {
        destinations = List.copyOf(destinations);
        subscriptions = List.copyOf(subscriptions);
    }
    @Override public ComponentTypeV2 type() { return ComponentTypeV2.BROKER; }
}
