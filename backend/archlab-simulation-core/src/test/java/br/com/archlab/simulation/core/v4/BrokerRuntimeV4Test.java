package br.com.archlab.simulation.core.v4;

import br.com.archlab.simulation.core.v2.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BrokerRuntimeV4Test {
    private static ArchitectureV2 queue(boolean consumers) {
        var broker = new BrokerV2("b","Broker",List.of(new BrokerDestinationV2("orders",IntegrationKindV2.QUEUE)),
                consumers ? List.of(new BrokerSubscriptionV2("orders","a1","ba1"),
                        new BrokerSubscriptionV2("orders","a2","ba2")) : List.of());
        var components = consumers ? List.<ComponentV2>of(new ClientV2("c","Client"),broker,
                new ApiV2("a1","A1",1,1),new ApiV2("a2","A2",1,1))
                : List.<ComponentV2>of(new ClientV2("c","Client"),broker);
        var connections = consumers ? List.of(
                new ConnectionV2("cb","c","b",10,new QueueIntegrationV2("orders")),
                new ConnectionV2("ba1","b","a1",3,new QueueIntegrationV2("orders")),
                new ConnectionV2("ba2","b","a2",4,new QueueIntegrationV2("orders")))
                : List.of(new ConnectionV2("cb","c","b",10,new QueueIntegrationV2("orders")));
        return new ArchitectureV2(components,connections);
    }

    @Test void queueRoundRobinSelectsOneConsumerPerPublication() {
        var graph=queue(true);
        var result=new BrokerRuntimeV4().run(graph,graph.connections().get(0),
                (BrokerV2)graph.components().get(1),"orders",3);
        assertEquals(List.of("a1","a2","a1"),result.deliveries().stream().map(BrokerRuntimeV4.Delivery::consumerId).toList());
        assertEquals(3,result.deliveries().size());
        assertEquals(16,result.totalDurationMs());
        assertEquals(ExecutionLifecycleV4.GlobalStatus.COMPLETED,result.status());
    }

    @Test void queueWithoutConsumerWaitsThirtyVirtualSeconds() {
        var graph=queue(false);
        var result=new BrokerRuntimeV4().run(graph,graph.connections().get(0),
                (BrokerV2)graph.components().get(1),"orders",1);
        assertEquals(30_010,result.totalDurationMs());
        assertEquals(ExecutionLifecycleV4.MainStatus.COMPLETED,result.mainStatus());
        assertEquals(ExecutionLifecycleV4.GlobalStatus.COMPLETED_WITH_ERRORS,result.status());
        assertTrue(result.deliveries().isEmpty());
    }

    @Test void topicAndMqttFanOutAndAllowZeroSubscribers() {
        for (IntegrationKindV2 kind : List.of(IntegrationKindV2.TOPIC, IntegrationKindV2.MQTT)) {
            IntegrationV2 integration = kind == IntegrationKindV2.TOPIC
                    ? new TopicIntegrationV2("orders") : new MqttIntegrationV2("orders",0);
            var broker = new BrokerV2("b","Broker",List.of(new BrokerDestinationV2("orders",kind)),
                    List.of(new BrokerSubscriptionV2("orders","a1","ba1"),
                            new BrokerSubscriptionV2("orders","a2","ba2")));
            var graph = new ArchitectureV2(List.of(new ClientV2("c","Client"),broker,
                    new ApiV2("a1","A1",0,0),new ApiV2("a2","A2",0,0)),List.of(
                    new ConnectionV2("cb","c","b",10,integration),
                    new ConnectionV2("ba1","b","a1",2,integration),
                    new ConnectionV2("ba2","b","a2",3,integration)));
            var result = new BrokerRuntimeV4().run(graph,graph.connections().get(0),broker,"orders",1);
            assertEquals(List.of("a1","a2"),result.deliveries().stream().map(BrokerRuntimeV4.Delivery::consumerId).toList());
            assertEquals(13,result.totalDurationMs());
            var emptyBroker = new BrokerV2("b","Broker",broker.destinations(),List.of());
            var emptyGraph = new ArchitectureV2(List.of(new ClientV2("c","Client"),emptyBroker),
                    List.of(new ConnectionV2("cb","c","b",10,integration)));
            var empty = new BrokerRuntimeV4().run(emptyGraph,emptyGraph.connections().get(0),emptyBroker,"orders",1);
            assertTrue(empty.deliveries().isEmpty());
            assertEquals(10,empty.totalDurationMs());
            assertEquals(ExecutionLifecycleV4.GlobalStatus.COMPLETED,empty.status());
        }
    }
}
