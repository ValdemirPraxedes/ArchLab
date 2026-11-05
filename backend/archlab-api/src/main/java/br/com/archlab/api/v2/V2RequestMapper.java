package br.com.archlab.api.v2;

import br.com.archlab.simulation.core.v2.*;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.*;

/** Strict transport decoder. Syntax/type errors -> 400; domain errors -> validator. */
@Component
public class V2RequestMapper {
    public ArchitectureV2 architecture(JsonNode node) {
        return architecture(node, false);
    }

    public ArchitectureV2 canonicalArchitecture(JsonNode node) {
        return architecture(node, true);
    }

    private ArchitectureV2 architecture(JsonNode node, boolean canonical) {
        object(node,"components","connections");
        List<ComponentV2> components=new ArrayList<>();
        for(JsonNode c:array(node,"components")) {
            String type=text(c,"type");
            switch(type) {
                case "CLIENT" -> { object(c,"id","type","displayName");
                    components.add(new ClientV2(text(c,"id"),text(c,"displayName"))); }
                case "WEB_BROWSER", "MOBILE_APP", "DESKTOP_APP", "CLI", "BOT", "EMBEDDED_SYSTEM" -> {
                    if (!canonical) throw invalid("Unsupported component type");
                    object(c,"id","type","displayName");
                    components.add(new ClientV2(text(c,"id"),text(c,"displayName"),
                            ComponentTypeV2.valueOf(type)));
                }
                case "API" -> { object(c,"id","type","displayName","preProcessingMs","postProcessingMs","behaviors");
                    components.add(new ApiV2(text(c,"id"),text(c,"displayName"),
                            canonical ? numberOrZero(c,"preProcessingMs") : number(c,"preProcessingMs"),
                            canonical ? numberOrZero(c,"postProcessingMs") : number(c,"postProcessingMs"))); }
                case "DATABASE" -> { object(c,"id","type","displayName","processingMs");
                    components.add(new DatabaseV2(text(c,"id"),text(c,"displayName"),number(c,"processingMs"))); }
                case "BROKER" -> {
                    if (!canonical) throw invalid("Unsupported component type");
                    object(c,"id","type","displayName","destinations","subscriptions");
                    List<BrokerDestinationV2> destinations=new ArrayList<>();
                    for (JsonNode d:arrayOrEmpty(c,"destinations")) {
                        object(d,"name","kind");
                        IntegrationKindV2 destinationKind;
                        try { destinationKind=IntegrationKindV2.valueOf(text(d,"kind")); }
                        catch (IllegalArgumentException ex) { throw invalid("Unsupported broker destination kind"); }
                        if (!destinationKind.isMessaging()) throw invalid("Unsupported broker destination kind");
                        destinations.add(new BrokerDestinationV2(longText(d,"name",256),destinationKind));
                    }
                    List<BrokerSubscriptionV2> subscriptions=new ArrayList<>();
                    for (JsonNode s:arrayOrEmpty(c,"subscriptions")) {
                        object(s,"destinationName","consumerComponentId","connectionId");
                        subscriptions.add(new BrokerSubscriptionV2(longText(s,"destinationName",256),
                                text(s,"consumerComponentId"),text(s,"connectionId")));
                    }
                    components.add(new BrokerV2(text(c,"id"),text(c,"displayName"),destinations,subscriptions));
                }
                default -> throw invalid("Unsupported component type");
            }
        }
        List<ConnectionV2> connections=new ArrayList<>();
        for(JsonNode c:array(node,"connections")) {
            object(c,"id","sourceId","targetId","latencyMs","integration","apiProcessingOverride");
            connections.add(new ConnectionV2(text(c,"id"),text(c,"sourceId"),text(c,"targetId"),
                    canonical ? numberOrZero(c,"latencyMs") : number(c,"latencyMs"),
                    integration(required(c,"integration"),canonical),
                    has(c,"apiProcessingOverride") ? override(required(c,"apiProcessingOverride")) : null));
        }
        return new ArchitectureV2(components,connections);
    }
    private static ApiProcessingOverrideV2 override(JsonNode node) {
        object(node,"preProcessingMs","postProcessingMs");
        // Both fields must exist; no partial fallback to API defaults.
        return new ApiProcessingOverrideV2(number(node,"preProcessingMs"),
                number(node,"postProcessingMs"));
    }

    public Request scenarioRequest(JsonNode root) {
        return scenarioRequest(root, false);
    }

    /** Canonical v1 request/response transport; v2 wire format remains unchanged. */
    public Request canonicalRequest(JsonNode root) {
        return scenarioRequest(root, true);
    }

    private Request scenarioRequest(JsonNode root, boolean canonical) {
        object(root,"architecture","scenario");
        ArchitectureV2 architecture=canonical ? canonicalArchitecture(required(root,"architecture"))
                : architecture(required(root,"architecture"));
        JsonNode s=required(root,"scenario");
        if (canonical) {
            object(s,"kind","entryComponentId","entryConnectionId","apiExecutions");
            if (!"REQUEST_RESPONSE".equals(text(s,"kind")))
                throw invalid("Unsupported scenario.kind for current request/response engine");
        } else {
            object(s,"entryComponentId","entryConnectionId","apiExecutions");
        }
        List<ApiExecutionV2> selections=new ArrayList<>();
        for(JsonNode a:array(s,"apiExecutions")) {
            object(a,"componentId","dependencyConnectionId");
            selections.add(new ApiExecutionV2(text(a,"componentId"),text(a,"dependencyConnectionId")));
        }
        return new Request(architecture,new ScenarioV2(text(s,"entryComponentId"),
                text(s,"entryConnectionId"),selections));
    }
    private IntegrationV2 integration(JsonNode node, boolean canonical) {
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
            case "HTTP_GENERIC" -> {
                if (!canonical) throw invalid("Unsupported integration kind");
                object(node,"kind","method","path");
                String method=text(node,"method");
                if (!Set.of("GET","POST","PUT","PATCH","DELETE","HEAD","OPTIONS").contains(method))
                    throw invalid("Unsupported HTTP method");
                yield new HttpGenericIntegrationV2(method,longText(node,"path",2048));
            }
            case "GRAPHQL_HTTP" -> {
                if (!canonical) throw invalid("Unsupported integration kind");
                object(node,"kind","operationType","operationName");
                String operationType=text(node,"operationType");
                if (!Set.of("QUERY","MUTATION").contains(operationType)) throw invalid("Unsupported GraphQL operation type");
                yield new GraphqlHttpIntegrationV2(operationType,longText(node,"operationName",256));
            }
            case "GRPC_UNARY" -> {
                if (!canonical) throw invalid("Unsupported integration kind");
                object(node,"kind","service","method");
                yield new GrpcUnaryIntegrationV2(longText(node,"service",256),longText(node,"method",256));
            }
            case "COAP" -> {
                if (!canonical) throw invalid("Unsupported integration kind");
                object(node,"kind","method","resource","mode");
                String method=text(node,"method"), mode=has(node,"mode")?text(node,"mode"):"CON";
                if (!Set.of("GET","POST","PUT","DELETE").contains(method) || !Set.of("CON","NON").contains(mode))
                    throw invalid("Unsupported CoAP method or mode");
                yield new CoapIntegrationV2(method,longText(node,"resource",2048),mode);
            }
            case "WEBSOCKET" -> {
                if (!canonical) throw invalid("Unsupported integration kind");
                object(node,"kind","path","connectTimeoutMs","idleTimeoutMs","closeMode","messages");
                String mode=has(node,"closeMode")?text(node,"closeMode"):"AUTOMATIC";
                if (!Set.of("AUTOMATIC","EXPLICIT").contains(mode)) throw invalid("Unsupported WebSocket close mode");
                List<WebSocketMessageV2> messages=new ArrayList<>();
                for (JsonNode message:arrayOrEmpty(node,"messages")) {
                    object(message,"sender","intervalMs","payload");
                    String sender=text(message,"sender");
                    if (!Set.of("SOURCE","TARGET").contains(sender)) throw invalid("Unsupported WebSocket sender");
                    messages.add(new WebSocketMessageV2(sender,numberOrZero(message,"intervalMs"),
                            has(message,"payload")?longText(message,"payload",32768):null));
                }
                if (messages.size()>100) throw invalid("Too many WebSocket messages");
                Long idle=has(node,"idleTimeoutMs") && !node.get("idleTimeoutMs").isNull()
                        ? number(node,"idleTimeoutMs") : null;
                yield new WebSocketIntegrationV2(longText(node,"path",2048),
                        has(node,"connectTimeoutMs")?number(node,"connectTimeoutMs"):5000L,
                        idle,mode,messages);
            }
            case "QUEUE" -> {
                if (!canonical) throw invalid("Unsupported integration kind");
                object(node,"kind","queueName");
                yield new QueueIntegrationV2(longText(node,"queueName",256));
            }
            case "TOPIC" -> {
                if (!canonical) throw invalid("Unsupported integration kind");
                object(node,"kind","topic");
                yield new TopicIntegrationV2(longText(node,"topic",256));
            }
            case "MQTT" -> {
                if (!canonical) throw invalid("Unsupported integration kind");
                object(node,"kind","topic","qos");
                yield new MqttIntegrationV2(longText(node,"topic",256),has(node,"qos")?intNumber(node,"qos"):0);
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
    private static Iterable<JsonNode> arrayOrEmpty(JsonNode n,String key) {
        return has(n,key) ? array(n,key) : List.of();
    }
    private static String text(JsonNode n,String key) {
        JsonNode v=required(n,key);
        if(!v.isTextual())throw invalid("Expected string: "+key);
        return v.asText();
    }
    private static String longText(JsonNode n,String key,int max) {
        JsonNode v=required(n,key);
        if (!v.isTextual() || v.asText().isBlank() || v.asText().codePointCount(0,v.asText().length())>max)
            throw invalid("Invalid text: "+key);
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
    private static long numberOrZero(JsonNode n,String key) {
        return has(n,key) ? number(n,key) : 0L;
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
