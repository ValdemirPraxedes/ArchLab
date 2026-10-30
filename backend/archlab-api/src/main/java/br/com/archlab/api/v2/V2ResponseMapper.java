package br.com.archlab.api.v2;

import br.com.archlab.simulation.core.event.*;
import br.com.archlab.simulation.core.v2.*;
import org.springframework.stereotype.Component;
import java.util.*;

/** Adapt v2 immutable core histories to documented OpenAPI v2 structures. */
@Component
public class V2ResponseMapper {
    public Map<String,Object> map(NestedExecutionResultV2 result,ArchitectureV2 architecture,ScenarioV2 scenario) {
        Map<String,Object> output=new LinkedHashMap<>();
        output.put("execution",fields("id","exec-001","status","COMPLETED",
                "requestId",result.requestId(),"entryComponentId",scenario.entryComponentId(),
                "entryConnectionId",scenario.entryConnectionId(),"totalDurationMs",result.totalDurationMs()));
        Map<String,String> componentStates=new LinkedHashMap<>();
        for(var component:architecture.components())componentStates.put(component.id(),"IDLE");
        output.put("initialState",fields("components",componentStates,
                "requests",Map.of(result.requestId(),"CREATED"),"exchanges",Map.of()));
        output.put("trace",trace(result,architecture));
        output.put("timeline",timeline(result));
        output.put("metrics",result.metrics());
        output.put("messageRepresentations",result.messageRepresentations().stream().map(representation ->
                fields("connectionId",representation.connectionId(),
                        "integration",integration(representation.integration()))).toList());
        return output;
    }
    private List<Map<String,Object>> trace(NestedExecutionResultV2 result,ArchitectureV2 architecture) {
        Map<String,ExchangeTraceV2> exchanges=new HashMap<>();
        for(var x:result.exchanges())exchanges.put(x.exchangeId(),x);
        Map<String,String> dbs=new HashMap<>();
        for(var c:architecture.components())if(c instanceof DatabaseV2)dbs.put(c.id(),c.id());
        List<Timed> entries=new ArrayList<>();
        int ordinal=0;
        for(var t:result.componentTransitions())
            entries.add(new Timed(t.timestampMs(),ordinal++,fields("type","STATE_TRANSITION",
                    "entityKind","COMPONENT","entityId",t.componentId(),
                    "previousState",t.from().name(),"newState",t.to().name(),"cause","EVENT")));
        for(var m:result.messages()){
            var exchange=exchanges.get(m.exchangeId());
            entries.add(new Timed(m.timestampMs(),ordinal++,fields("type","MESSAGE_EVENT",
                    "action",m.phase(),"messageId",m.messageId(),"requestId",m.requestId(),
                    "exchangeId",m.exchangeId(),"parentExchangeId",exchange.parentExchangeId(),
                    "connectionId",exchange.connectionId(),"sourceId",m.sourceId(),
                    "targetId",m.targetId(),"direction",m.direction(),
                    "intent",dbs.containsKey(exchange.targetId())?"DATABASE_QUERY":"API_REQUEST")));
        }
        Map<String,Deque<String>> activePhase=new HashMap<>();
        Map<String,Integer> counts=new HashMap<>();
        for(var event:result.events()) {
            var e=event.event();
            if(e instanceof ProcessingStarted p) {
                int index=counts.merge(p.componentId(),1,Integer::sum);
                String phase=dbs.containsKey(p.componentId())?"DATABASE_PROCESSING":
                        (index==1?"API_PRE_PROCESSING":"API_POST_PROCESSING");
                activePhase.computeIfAbsent(p.componentId(),k->new ArrayDeque<>()).push(phase);
                entries.add(new Timed(event.timestampMs(),ordinal++,fields("type","PROCESSING_EVENT",
                        "action","STARTED","componentId",p.componentId(),"requestId",p.requestId(),"phase",phase)));
            } else if(e instanceof ProcessingCompleted p) {
                Deque<String> phases=activePhase.get(p.componentId());
                String phase=phases.pop();
                entries.add(new Timed(event.timestampMs(),ordinal++,fields("type","PROCESSING_EVENT",
                        "action","COMPLETED","componentId",p.componentId(),"requestId",p.requestId(),"phase",phase)));
            }
        }
        entries.sort(Comparator.comparingLong(Timed::timestamp).thenComparingInt(Timed::ordinal));
        List<Map<String,Object>> out=new ArrayList<>();
        for(var t:entries) {
            Map<String,Object> item=new LinkedHashMap<>();
            item.put("sequence",out.size()+1L);
            item.put("timestampMs",t.timestamp());
            item.putAll(t.data());
            out.add(item);
        }
        return out;
    }
    private List<Map<String,Object>> timeline(NestedExecutionResultV2 result) {
        List<Map<String,Object>> out=new ArrayList<>();
        for(var a:result.timeline()) {
            Map<String,Object> item=fields("id",a.id(),"kind",a.kind(),"startMs",a.startMs(),
                    "endMs",a.endMs(),"durationMs",a.durationMs());
            if(a instanceof ProcessingActivityV2 p) {
                item.put("componentId",p.componentId());
                item.put("requestId",p.requestId());item.put("phase",p.phase());
            } else if(a instanceof CommunicationActivityV2 c) {
                item.put("messageId",c.messageId());item.put("exchangeId",c.exchangeId());
                item.put("connectionId",c.connectionId());item.put("sourceId",c.sourceId());
                item.put("targetId",c.targetId());
            }
            out.add(item);
        }
        return out;
    }
    private static Map<String,Object> integration(IntegrationV2 integration) {
        Map<String,Object> fields=new LinkedHashMap<>();
        fields.put("kind",integration.kind().name());
        if(integration instanceof HttpRestIntegrationV2 rest) {
            fields.put("request",rest.request());
            fields.put("response",rest.response());
        } else if(integration instanceof SoapHttpIntegrationV2 soap) {
            fields.put("soapVersion",soap.soapVersion());
            if(soap.operation()!=null)fields.put("operation",soap.operation());
            if(soap.action()!=null)fields.put("action",soap.action());
            if(soap.requestEnvelope()!=null)fields.put("requestEnvelope",soap.requestEnvelope());
            if(soap.responseEnvelope()!=null)fields.put("responseEnvelope",soap.responseEnvelope());
            if(soap.httpStatusCode()!=null)fields.put("httpStatusCode",soap.httpStatusCode());
            if(soap.requestHeaders()!=null)fields.put("requestHeaders",soap.requestHeaders());
            if(soap.responseHeaders()!=null)fields.put("responseHeaders",soap.responseHeaders());
        } else if(integration instanceof DatabaseQueryIntegrationV2 query) {
            if(query.queryText()!=null)fields.put("queryText",query.queryText());
            if(query.responseText()!=null)fields.put("responseText",query.responseText());
        }
        return fields;
    }

    private static Map<String,Object> fields(Object... args) {
        Map<String,Object> r=new LinkedHashMap<>();
        for(int i=0;i<args.length;i+=2)r.put((String)args[i],args[i+1]);
        return r;
    }
    private record Timed(long timestamp,int ordinal,Map<String,Object> data) {}
}
