package br.com.archlab.simulation.core.v4;

import br.com.archlab.simulation.core.v2.*;
import java.util.List;

/** Shared deterministic Queue round-robin and Topic/MQTT fan-out selection. */
public final class BrokerRoutingV4 {
    private BrokerRoutingV4() {}
    public static List<BrokerSubscriptionV2> eligible(BrokerV2 broker,String destinationName) {
        return broker.subscriptions().stream()
                .filter(subscription -> destinationName.equals(subscription.destinationName())).toList();
    }
    public static List<BrokerSubscriptionV2> recipients(List<BrokerSubscriptionV2> eligible,
                                                         IntegrationKindV2 kind,int zeroBasedSequence) {
        if (eligible.isEmpty()) return List.of();
        return kind == IntegrationKindV2.QUEUE
                ? List.of(eligible.get(Math.floorMod(zeroBasedSequence,eligible.size()))) : eligible;
    }
}
