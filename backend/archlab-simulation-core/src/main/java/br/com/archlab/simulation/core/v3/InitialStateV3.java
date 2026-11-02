package br.com.archlab.simulation.core.v3;
import java.util.*;
/** Optional initial values remain absent rather than inventing a transport default. */
public sealed interface InitialStateV3 {
    record Component(IdentifierV3 componentId,Boolean available) implements InitialStateV3 {
        public Component {Objects.requireNonNull(componentId);}
    }
    record Resource(IdentifierV3 resourceId,Boolean available) implements InitialStateV3 {
        public Resource {Objects.requireNonNull(resourceId);}
    }
    record Policy(IdentifierV3 policyInstanceId,String state) implements InitialStateV3 {
        public Policy {Objects.requireNonNull(policyInstanceId);if(state!=null && state.isEmpty())throw new IllegalArgumentException("Empty policy state");}
    }
    record Queue(IdentifierV3 queueId,Long initialMessageCount) implements InitialStateV3 {
        public Queue {Objects.requireNonNull(queueId);if(initialMessageCount!=null && initialMessageCount<0)throw new IllegalArgumentException("Negative message count");}
    }
}
