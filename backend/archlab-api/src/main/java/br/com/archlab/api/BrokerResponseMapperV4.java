package br.com.archlab.api;

import br.com.archlab.simulation.core.v2.*;
import br.com.archlab.simulation.core.v4.BrokerRuntimeV4;
import java.util.*;

/** Public result projection for finite, virtual Queue/Topic/MQTT publications. */
final class BrokerResponseMapperV4 {
    private record Timed(long at, int order, Map<String,Object> data) {}
    private final List<Timed> events = new ArrayList<>();
    private final List<Map<String,Object>> timeline = new ArrayList<>();

    Map<String,Object> map(ArchitectureV2 architecture, ConnectionV2 entry,
                           BrokerRuntimeV4.Result run) {
        Map<String,ConnectionV2> connections = new HashMap<>();
        architecture.connections().forEach(c -> connections.put(c.id(),c));
        event(0,fields("type","STATE_TRANSITION","entityKind","REQUEST","entityId","publish-1",
                "previousState","CREATED","newState","IN_PROGRESS","cause","PUBLISH_STARTED"));
        for (var publication : run.publications()) {
            event(0,fields("type","STATE_TRANSITION","entityKind","BROKER_MESSAGE","entityId",publication.id(),
                    "previousState",null,"newState","CREATED","cause","MESSAGE_CREATED"));
            message(0,"SENT",publication.id(),"publish-1",publication.id(),null,entry,
                    entry.sourceId(),entry.targetId(),"BROKER_PUBLISH");
            message(publication.acceptedAtMs(),"ARRIVED",publication.id(),"publish-1",publication.id(),null,
                    entry,entry.sourceId(),entry.targetId(),"BROKER_PUBLISH");
            communication(publication.id(),publication.id(),entry,entry.sourceId(),entry.targetId(),
                    publication.sentAtMs(),publication.acceptedAtMs());
            event(publication.acceptedAtMs(),fields("type","STATE_TRANSITION","entityKind","BROKER_MESSAGE",
                    "entityId",publication.id(),"previousState","CREATED","newState","ACCEPTED",
                    "cause","BROKER_ACCEPTED"));
            if (publication.timedOutWithoutConsumer()) {
                event(publication.acceptedAtMs(),fields("type","STATE_TRANSITION","entityKind","BROKER_MESSAGE",
                        "entityId",publication.id(),"previousState","ACCEPTED","newState","QUEUED",
                        "cause","NO_ELIGIBLE_CONSUMER"));
                event(run.totalDurationMs(),fields("type","STATE_TRANSITION","entityKind","BROKER_MESSAGE",
                        "entityId",publication.id(),"previousState","QUEUED","newState","TIMED_OUT",
                        "cause","QUEUE_WAIT_TIMEOUT"));
            } else {
                event(publication.acceptedAtMs(),fields("type","STATE_TRANSITION","entityKind","BROKER_MESSAGE",
                        "entityId",publication.id(),"previousState","ACCEPTED","newState","COMPLETED",
                        "cause","PUBLICATION_ACCEPTED"));
            }
        }
        long accepted = run.publications().get(0).acceptedAtMs();
        event(accepted,fields("type","STATE_TRANSITION","entityKind","REQUEST","entityId","publish-1",
                "previousState","IN_PROGRESS","newState","COMPLETED","cause","PUBLISH_ACCEPTED"));
        for (var delivery : run.deliveries()) {
            ConnectionV2 connection = connections.get(delivery.connectionId());
            event(delivery.sentAtMs(),fields("type","STATE_TRANSITION","entityKind","DELIVERY","entityId",delivery.id(),
                    "previousState",null,"newState","CREATED","cause","DELIVERY_CREATED"));
            message(delivery.sentAtMs(),"SENT",delivery.id(),"publish-1",delivery.id(),delivery.messageId(),
                    connection,connection.sourceId(),connection.targetId(),"BROKER_DELIVERY");
            message(delivery.arrivedAtMs(),"ARRIVED",delivery.id(),"publish-1",delivery.id(),delivery.messageId(),
                    connection,connection.sourceId(),connection.targetId(),"BROKER_DELIVERY");
            communication(delivery.id(),delivery.id(),connection,connection.sourceId(),connection.targetId(),
                    delivery.sentAtMs(),delivery.arrivedAtMs());
            event(delivery.arrivedAtMs(),fields("type","STATE_TRANSITION","entityKind","DELIVERY","entityId",delivery.id(),
                    "previousState","CREATED","newState","PROCESSING","cause","DELIVERY_ARRIVED"));
            event(delivery.arrivedAtMs(),fields("type","PROCESSING_EVENT","action","STARTED",
                    "componentId",delivery.consumerId(),"requestId","publish-1","phase","API_MESSAGE_PROCESSING"));
            event(delivery.completedAtMs(),fields("type","PROCESSING_EVENT","action","COMPLETED",
                    "componentId",delivery.consumerId(),"requestId","publish-1","phase","API_MESSAGE_PROCESSING"));
            event(delivery.completedAtMs(),fields("type","STATE_TRANSITION","entityKind","DELIVERY","entityId",delivery.id(),
                    "previousState","PROCESSING","newState","COMPLETED","cause","CONSUMER_PROCESSED"));
            timeline.add(fields("id","activity-"+(timeline.size()+1),"kind","PROCESSING",
                    "componentId",delivery.consumerId(),"requestId","publish-1","phase","API_MESSAGE_PROCESSING",
                    "startMs",delivery.arrivedAtMs(),"endMs",delivery.completedAtMs(),
                    "durationMs",delivery.completedAtMs()-delivery.arrivedAtMs()));
        }
        event(run.totalDurationMs(),fields("type","STATE_TRANSITION","entityKind","EXECUTION","entityId","exec-001",
                "previousState","ACTIVE","newState",run.status().name(),"cause","ALL_RESOURCES_TERMINAL"));
        events.sort(Comparator.comparingLong(Timed::at).thenComparingInt(Timed::order));
        List<Map<String,Object>> trace = new ArrayList<>();
        for (Timed item : events) {
            Map<String,Object> row=fields("sequence",trace.size()+1,"timestampMs",item.at());
            row.putAll(item.data()); trace.add(row);
        }
        timeline.sort(Comparator.comparingLong((Map<String,Object> a) -> (long)a.get("startMs"))
                .thenComparing(a -> (String)a.get("id")));
        Map<String,String> components = new LinkedHashMap<>();
        architecture.components().forEach(c -> components.put(c.id(),"IDLE"));
        List<Map<String,Object>> resourceStates = run.resources().values().stream().map(r ->
                fields("id",r.id(),"kind",r.kind().name(),"status",r.status().name())).toList();
        return fields("execution",fields("id","exec-001","status",run.status().name(),
                        "mainStatus",run.mainStatus().name(),"requestId","publish-1",
                        "entryComponentId",entry.sourceId(),"entryConnectionId",entry.id(),
                        "totalDurationMs",run.totalDurationMs()),
                "initialState",fields("components",components,"requests",Map.of("publish-1","CREATED"),"exchanges",Map.of()),
                "trace",trace,"timeline",timeline,"metrics",metrics(entry,run,connections),
                "messageRepresentations",representations(entry,run,connections),"resourceStates",resourceStates);
    }

    private Map<String,Object> metrics(ConnectionV2 entry,BrokerRuntimeV4.Result run,Map<String,ConnectionV2> connections) {
        Map<String,Long> processing=new LinkedHashMap<>();
        for (var delivery:run.deliveries()) processing.merge(delivery.consumerId(),
                delivery.completedAtMs()-delivery.arrivedAtMs(),Long::sum);
        List<Map<String,Object>> processingMetrics=processing.entrySet().stream().map(e ->
                fields("componentId",e.getKey(),"durationMs",e.getValue())).toList();
        Map<String,long[]> communication=new LinkedHashMap<>();
        for (var activity:timeline) if ("COMMUNICATION".equals(activity.get("kind"))) {
            long[] counts=communication.computeIfAbsent((String)activity.get("connectionId"),k -> new long[2]);
            counts[0]++; counts[1]+=(long)activity.get("durationMs");
        }
        List<Map<String,Object>> communicationMetrics=communication.entrySet().stream().map(e ->
                fields("connectionId",e.getKey(),"traversals",e.getValue()[0],"durationMs",e.getValue()[1])).toList();
        List<Map<String,Object>> waits=run.publications().stream().filter(BrokerRuntimeV4.Publication::timedOutWithoutConsumer)
                .map(p -> fields("componentId",entry.targetId(),"exchangeId",p.id(),
                        "startMs",p.acceptedAtMs(),"endMs",run.totalDurationMs(),
                        "durationMs",run.totalDurationMs()-p.acceptedAtMs())).toList();
        int failed=(int)run.publications().stream().filter(BrokerRuntimeV4.Publication::timedOutWithoutConsumer).count();
        return fields("totalDurationMs",run.totalDurationMs(),"processingByComponent",processingMetrics,
                "communicationByConnection",communicationMetrics,"waitingByComponent",waits,"exchanges",List.of(),
                "messaging",List.of(fields("connectionId",entry.id(),"published",run.publications().size(),
                        "delivered",run.deliveries().size(),"processed",run.deliveries().size(),
                        "pending",0,"failures",failed,"eligibleSubscribers",run.eligibleSubscribers())));
    }

    private List<Map<String,Object>> representations(ConnectionV2 entry,BrokerRuntimeV4.Result run,
                                                     Map<String,ConnectionV2> connections) {
        Map<String,ConnectionV2> used=new LinkedHashMap<>();
        used.put(entry.id(),entry);
        for (var delivery:run.deliveries()) used.putIfAbsent(delivery.connectionId(),connections.get(delivery.connectionId()));
        return used.values().stream().map(c -> {
            Map<String,Object> integration=fields("kind",c.integration().kind().name());
            if (c.integration() instanceof QueueIntegrationV2 queue) integration.put("queueName",queue.queueName());
            if (c.integration() instanceof TopicIntegrationV2 topic) integration.put("topic",topic.topic());
            if (c.integration() instanceof MqttIntegrationV2 mqtt) {
                integration.put("topic",mqtt.topic()); integration.put("qos",mqtt.qos());
            }
            return fields("connectionId",c.id(),"integration",integration);
        }).toList();
    }

    private void event(long at,Map<String,Object> data) { events.add(new Timed(at,events.size(),data)); }
    private void message(long at,String action,String messageId,String requestId,String exchangeId,String parent,
                         ConnectionV2 connection,String source,String target,String intent) {
        event(at,fields("type","MESSAGE_EVENT","action",action,"messageId",messageId,
                "requestId",requestId,"exchangeId",exchangeId,"parentExchangeId",parent,
                "connectionId",connection.id(),"sourceId",source,"targetId",target,
                "direction","MESSAGE","intent",intent));
    }
    private void communication(String messageId,String exchangeId,ConnectionV2 connection,String source,String target,
                               long start,long end) {
        timeline.add(fields("id","activity-"+(timeline.size()+1),"kind","COMMUNICATION",
                "messageId",messageId,"exchangeId",exchangeId,"connectionId",connection.id(),
                "sourceId",source,"targetId",target,"startMs",start,"endMs",end,"durationMs",end-start));
    }
    private static Map<String,Object> fields(Object... values) {
        Map<String,Object> map=new LinkedHashMap<>();
        for(int i=0;i<values.length;i+=2)map.put((String)values[i],values[i+1]);
        return map;
    }
}
