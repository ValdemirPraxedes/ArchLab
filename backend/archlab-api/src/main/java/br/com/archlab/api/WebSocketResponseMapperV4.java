package br.com.archlab.api;

import br.com.archlab.simulation.core.v2.ArchitectureV2;
import br.com.archlab.simulation.core.v2.ConnectionV2;
import br.com.archlab.simulation.core.v2.WebSocketIntegrationV2;
import br.com.archlab.simulation.core.v4.WebSocketRuntimeV4;
import java.util.*;

/** Public v0.4 projection of a virtual WebSocket session. */
final class WebSocketResponseMapperV4 {
    private final List<Map<String,Object>> trace = new ArrayList<>();
    private final List<Map<String,Object>> timeline = new ArrayList<>();

    Map<String,Object> map(ArchitectureV2 architecture, ConnectionV2 connection,
                           WebSocketIntegrationV2 integration, WebSocketRuntimeV4.Result run) {
        String source = connection.sourceId(), target = connection.targetId();
        state(0,"REQUEST","request-1","CREATED","IN_PROGRESS","REQUEST_STARTED");
        state(0,"WEBSOCKET_SESSION","session-1",null,"CONNECTING","SESSION_CREATED");
        message(0,"SENT","open-1",connection,source,target,"REQUEST","WEBSOCKET_OPEN");
        if (run.openedAtMs() < 0) {
            state(run.totalDurationMs(),"WEBSOCKET_SESSION","session-1","CONNECTING","TIMED_OUT","CONNECT_TIMEOUT");
            state(run.totalDurationMs(),"REQUEST","request-1","IN_PROGRESS","TIMED_OUT","CONNECT_TIMEOUT");
        } else {
            message(run.openedAtMs(),"ARRIVED","open-1",connection,source,target,"REQUEST","WEBSOCKET_OPEN");
            activity("open-1",connection,source,target,0,run.openedAtMs());
            state(run.openedAtMs(),"WEBSOCKET_SESSION","session-1","CONNECTING","OPEN","SESSION_OPENED");
            for (var span : run.messages()) {
                String sender = "SOURCE".equals(span.message().sender()) ? source : target;
                String receiver = sender.equals(source) ? target : source;
                String id = "ws-message-" + span.index();
                message(span.sentAtMs(),"SENT",id,connection,sender,receiver,"MESSAGE","WEBSOCKET_MESSAGE");
                message(span.arrivedAtMs(),"ARRIVED",id,connection,sender,receiver,"MESSAGE","WEBSOCKET_MESSAGE");
                activity(id,connection,sender,receiver,span.sentAtMs(),span.arrivedAtMs());
            }
            boolean timedOut = run.mainStatus() == br.com.archlab.simulation.core.v4.ExecutionLifecycleV4.MainStatus.TIMED_OUT;
            if (timedOut) {
                state(run.totalDurationMs(),"WEBSOCKET_SESSION","session-1","OPEN","TIMED_OUT","IDLE_TIMEOUT");
            } else {
                String cause = "EXPLICIT".equals(integration.closeMode()) ? "EXPLICIT_CLOSE" : "AUTO_CLOSE";
                state(run.totalDurationMs(),"WEBSOCKET_SESSION","session-1","OPEN","CLOSING",cause);
                state(run.totalDurationMs(),"WEBSOCKET_SESSION","session-1","CLOSING","CLOSED",cause);
            }
            state(run.totalDurationMs(),"REQUEST","request-1","IN_PROGRESS",
                    timedOut ? "TIMED_OUT" : "COMPLETED",timedOut ? "IDLE_TIMEOUT" : "SESSION_CLOSED");
        }
        Map<String,String> components = new LinkedHashMap<>();
        architecture.components().forEach(c -> components.put(c.id(),"IDLE"));
        long communicationMs = timeline.stream().mapToLong(a -> (long)a.get("durationMs")).sum();
        long sourceMessages = run.messages().stream().filter(m -> "SOURCE".equals(m.message().sender())).count();
        long targetMessages = run.messages().size() - sourceMessages;
        List<Map<String,Object>> communication = timeline.isEmpty() ? List.of() : List.of(fields("connectionId",connection.id(),
                "traversals",(long)timeline.size(),"durationMs",communicationMs));
        Map<String,Object> metrics = fields("totalDurationMs",run.totalDurationMs(),
                "processingByComponent",List.of(),
                "communicationByConnection",communication,
                "waitingByComponent",List.of(),"exchanges",List.of(),
                "websocket",List.of(fields("connectionId",connection.id(),"sessionId","session-1",
                        "messagesSent",sourceMessages,"messagesReceived",targetMessages,"durationMs",run.totalDurationMs())));
        List<Map<String,Object>> messageDefinitions = integration.messages().stream().map(m -> {
            Map<String,Object> definition=fields("sender",m.sender(),"intervalMs",m.intervalMs());
            if (m.payload()!=null) definition.put("payload",m.payload());
            return definition;
        }).toList();
        Map<String,Object> representation = fields("kind","WEBSOCKET","path",integration.path(),
                "connectTimeoutMs",integration.connectTimeoutMs(),"idleTimeoutMs",integration.idleTimeoutMs(),
                "closeMode",integration.closeMode(),"messages",messageDefinitions);
        return fields("execution",fields("id","exec-001","status",run.status().name(),
                        "mainStatus",run.mainStatus().name(),"requestId","request-1",
                        "entryComponentId",source,"entryConnectionId",connection.id(),
                        "totalDurationMs",run.totalDurationMs()),
                "initialState",fields("components",components,"requests",Map.of("request-1","CREATED"),"exchanges",Map.of()),
                "trace",trace,"timeline",timeline,"metrics",metrics,
                "messageRepresentations",List.of(fields("connectionId",connection.id(),"integration",representation)),
                "resourceStates",List.of(fields("id","session-1","kind","WEBSOCKET_SESSION",
                        "status",run.mainStatus().name())));
    }

    private void state(long at,String kind,String id,String previous,String next,String cause) {
        trace.add(fields("sequence",trace.size()+1,"timestampMs",at,"type","STATE_TRANSITION",
                "entityKind",kind,"entityId",id,"previousState",previous,"newState",next,"cause",cause));
    }
    private void message(long at,String action,String id,ConnectionV2 connection,String source,String target,
                         String direction,String intent) {
        trace.add(fields("sequence",trace.size()+1,"timestampMs",at,"type","MESSAGE_EVENT",
                "action",action,"messageId",id,"requestId","request-1","exchangeId","session-1",
                "parentExchangeId",null,"connectionId",connection.id(),"sourceId",source,
                "targetId",target,"direction",direction,"intent",intent));
    }
    private void activity(String id,ConnectionV2 connection,String source,String target,long start,long end) {
        timeline.add(fields("id","activity-"+(timeline.size()+1),"kind","COMMUNICATION",
                "messageId",id,"exchangeId","session-1","connectionId",connection.id(),
                "sourceId",source,"targetId",target,"startMs",start,"endMs",end,"durationMs",end-start));
    }
    private static Map<String,Object> fields(Object... values) {
        Map<String,Object> map = new LinkedHashMap<>();
        for (int i=0;i<values.length;i+=2) map.put((String)values[i],values[i+1]);
        return map;
    }
}
