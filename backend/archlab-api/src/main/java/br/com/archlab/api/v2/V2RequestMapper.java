package br.com.archlab.api.v2;

import br.com.archlab.simulation.core.v2.*;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.*;

/** Strict transport decoder. Syntax/type errors -> 400; domain errors -> validator. */
@Component
public class V2RequestMapper {
    public ArchitectureV2 architecture(JsonNode node) {
        object(node,"components","connections");
        List<ComponentV2> components=new ArrayList<>();
        for(JsonNode c:array(node,"components")) {
            String type=text(c,"type");
            switch(type) {
                case "CLIENT" -> { object(c,"id","type","displayName");
                    components.add(new ClientV2(text(c,"id"),text(c,"displayName"))); }
                case "API" -> { object(c,"id","type","displayName","preProcessingMs","postProcessingMs");
                    components.add(new ApiV2(text(c,"id"),text(c,"displayName"),
                            number(c,"preProcessingMs"),number(c,"postProcessingMs"))); }
                case "DATABASE" -> { object(c,"id","type","displayName","processingMs");
                    components.add(new DatabaseV2(text(c,"id"),text(c,"displayName"),number(c,"processingMs"))); }
                default -> throw invalid("Unsupported component type");
            }
        }
        List<ConnectionV2> connections=new ArrayList<>();
        for(JsonNode c:array(node,"connections")) {
            object(c,"id","sourceId","targetId","latencyMs","integration");
            connections.add(new ConnectionV2(text(c,"id"),text(c,"sourceId"),text(c,"targetId"),
                    number(c,"latencyMs"),integration(required(c,"integration"))));
        }
        return new ArchitectureV2(components,connections);
    }
    public Request scenarioRequest(JsonNode root) {
        object(root,"architecture","scenario");
        ArchitectureV2 architecture=architecture(required(root,"architecture"));
        JsonNode s=required(root,"scenario");
        object(s,"entryComponentId","entryConnectionId","apiExecutions");
        List<ApiExecutionV2> selections=new ArrayList<>();
        for(JsonNode a:array(s,"apiExecutions")) {
            object(a,"componentId","dependencyConnectionId");
            selections.add(new ApiExecutionV2(text(a,"componentId"),text(a,"dependencyConnectionId")));
        }
        return new Request(architecture,new ScenarioV2(text(s,"entryComponentId"),
                text(s,"entryConnectionId"),selections));
    }
    private IntegrationV2 integration(JsonNode node) {
        String kind=text(node,"kind");
        return switch(kind) {
            case "HTTP_REST" -> {
                object(node,"kind","request","response");
                yield new HttpRestIntegrationV2(
                        has(node,"request")?request(required(node,"request")):null,
                        has(node,"response")?response(required(node,"response")):null);
            }
            case "SOAP_HTTP" -> {
                object(node,"kind","soapVersion","operation","action","requestEnvelope",
                        "responseEnvelope","httpStatusCode","requestHeaders","responseHeaders");
                yield new SoapHttpIntegrationV2(text(node,"soapVersion"),
                        optionalText(node,"operation"),optionalText(node,"action"),
                        optionalText(node,"requestEnvelope"),optionalText(node,"responseEnvelope"),
                        has(node,"httpStatusCode")?intNumber(node,"httpStatusCode"):null,
                        optionalHeaders(node,"requestHeaders"),optionalHeaders(node,"responseHeaders"));
            }
            case "DATABASE_QUERY" -> {
                object(node,"kind","queryText","responseText");
                yield new DatabaseQueryIntegrationV2(optionalText(node,"queryText"),optionalText(node,"responseText"));
            }
            default -> throw invalid("Unsupported integration kind");
        };
    }
    private static HttpRequestV2 request(JsonNode n) {
        object(n,"method","path","headers","body");
        HttpMethodV2 method;
        try {method=HttpMethodV2.valueOf(text(n,"method"));}
        catch(IllegalArgumentException ex){throw invalid("Unsupported HTTP method");}
        return new HttpRequestV2(method,text(n,"path"),optionalHeaders(n,"headers"),optionalText(n,"body"));
    }
    private static HttpResponseV2 response(JsonNode n) {
        object(n,"statusCode","headers","body");
        return new HttpResponseV2(intNumber(n,"statusCode"),optionalHeaders(n,"headers"),optionalText(n,"body"));
    }
    private static Map<String,String> optionalHeaders(JsonNode n,String name) {
        if(!has(n,name))return null;
        JsonNode headers=required(n,name);
        if(!headers.isObject())throw invalid("Headers must be an object");
        Map<String,String> result=new LinkedHashMap<>();
        for(String key:headers.propertyNames()) {
            JsonNode value=headers.get(key);
            if(!value.isTextual())throw invalid("Header values must be strings");
            result.put(key,value.asText());
        }
        return result;
    }
    private static void object(JsonNode node,String... keys) {
        if(node==null || !node.isObject())throw invalid("Expected object");
        Set<String> allowed=Set.of(keys);
        for(String key:node.propertyNames())if(!allowed.contains(key))throw invalid("Unexpected field: "+key);
    }
    private static JsonNode required(JsonNode n,String key) {
        JsonNode v=n.get(key);
        if(v==null || v.isNull())throw invalid("Missing field: "+key);
        return v;
    }
    private static boolean has(JsonNode n,String key) {return n.get(key)!=null;}
    private static JsonNode array(JsonNode n,String key) {
        JsonNode v=required(n,key);
        if(!v.isArray())throw invalid("Expected array: "+key);
        return v;
    }
    private static String text(JsonNode n,String key) {
        JsonNode v=required(n,key);
        if(!v.isTextual())throw invalid("Expected string: "+key);
        return v.asText();
    }
    private static String optionalText(JsonNode n,String key) {
        return has(n,key)?text(n,key):null;
    }
    private static long number(JsonNode n,String key) {
        JsonNode v=required(n,key);
        if(!v.isIntegralNumber()||!v.canConvertToLong())throw invalid("Expected int64: "+key);
        return v.longValue();
    }
    private static int intNumber(JsonNode n,String key) {
        long value=number(n,key);
        if(value<Integer.MIN_VALUE||value>Integer.MAX_VALUE)throw invalid("Expected int32: "+key);
        return (int)value;
    }
    private static IllegalArgumentException invalid(String detail) {
        return new IllegalArgumentException(detail);
    }
    public record Request(ArchitectureV2 architecture,ScenarioV2 scenario) {}
}
