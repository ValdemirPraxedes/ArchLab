package br.com.archlab.simulation.core.v3;
import java.util.*;
/** Explicit server-owned catalog; declarations never imply implemented runtime handlers. */
public record TypeCatalogV3(Map<String,ComponentType> components,Map<String,ResourceType> resources,
                            Set<String> protocols) {
    public TypeCatalogV3 {components=Map.copyOf(components);resources=Map.copyOf(resources);protocols=Set.copyOf(protocols);}
    public record ComponentType(Map<String,Class<? extends PropertyValueV3>> properties,
                                Set<String> capabilities,Set<String> nativeBehaviors) {
        public ComponentType {properties=Map.copyOf(properties);capabilities=Set.copyOf(capabilities);nativeBehaviors=Set.copyOf(nativeBehaviors);}
    }
    public record ResourceType(Set<String> ownerTypes,Map<String,Class<? extends PropertyValueV3>> properties) {
        public ResourceType {ownerTypes=Set.copyOf(ownerTypes);properties=Map.copyOf(properties);}
    }
    public static TypeCatalogV3 initial() {
        var common=Map.<String,Class<? extends PropertyValueV3>>of("available",PropertyValueV3.Flag.class);
        var processing=Map.<String,Class<? extends PropertyValueV3>>of("available",PropertyValueV3.Flag.class,
                "processingMs",PropertyValueV3.IntegerValue.class);
        var types=new LinkedHashMap<String,ComponentType>();
        types.put("CLIENT_GENERIC",new ComponentType(common,Set.of("CALL","DELAY","COMPLETE","FAIL"),Set.of()));
        types.put("API_SERVICE",new ComponentType(common,Set.of("PROCESS","CALL","PUBLISH","CONDITION","FORK","JOIN","DELAY","COMPLETE","FAIL"),Set.of()));
        types.put("DATABASE_GENERIC",new ComponentType(processing,Set.of("PROCESS","COMPLETE","FAIL"),Set.of("DATABASE_QUERY")));
        types.put("BROKER_GENERIC",new ComponentType(processing,Set.of("PUBLISH","COMPLETE","FAIL"),Set.of("BROKER_ACCEPT")));
        types.put("QUEUE_GENERIC",new ComponentType(common,Set.of("COMPLETE","FAIL"),Set.of("QUEUE_DELIVER")));
        types.put("TELEMETRY_GENERIC",new ComponentType(processing,Set.of("PROCESS","COMPLETE","FAIL"),Set.of("TELEMETRY_COLLECT")));
        var pools=Map.<String,Class<? extends PropertyValueV3>>of("capacity",PropertyValueV3.IntegerValue.class,"available",PropertyValueV3.Flag.class,"waitTimeoutMs",PropertyValueV3.IntegerValue.class);
        return new TypeCatalogV3(types,Map.of(
                "PROCESSING_POOL",new ResourceType(Set.of("API_SERVICE","DATABASE_GENERIC","TELEMETRY_GENERIC"),pools),
                "MESSAGE_QUEUE",new ResourceType(Set.of("QUEUE_GENERIC"),Map.ofEntries(Map.entry("capacity",PropertyValueV3.IntegerValue.class),Map.entry("ttlMs",PropertyValueV3.IntegerValue.class),Map.entry("consumerEndpointId",PropertyValueV3.Reference.class),Map.entry("consumerOperationId",PropertyValueV3.Reference.class),Map.entry("consumerConcurrency",PropertyValueV3.IntegerValue.class),Map.entry("deliveryLatencyMs",PropertyValueV3.IntegerValue.class),Map.entry("ackDelayMs",PropertyValueV3.IntegerValue.class),Map.entry("ackTimeoutMs",PropertyValueV3.IntegerValue.class),Map.entry("maxDeliveryAttempts",PropertyValueV3.IntegerValue.class),Map.entry("redeliveryDelayMs",PropertyValueV3.IntegerValue.class))),
                "POLICY_INSTANCE",new ResourceType(Set.of("API_SERVICE","CLIENT_GENERIC"),Map.of("timeoutMs",PropertyValueV3.IntegerValue.class,"attemptTimeoutMs",PropertyValueV3.IntegerValue.class,"maxAttempts",PropertyValueV3.IntegerValue.class,"retryDelayMs",PropertyValueV3.IntegerValue.class)),"TOPIC",new ResourceType(Set.of("BROKER_GENERIC"),Map.of("queueIds",PropertyValueV3.Values.class,"acceptanceMs",PropertyValueV3.IntegerValue.class))),Set.of("HTTP_REST","SOAP_HTTP","DATABASE_QUERY","MESSAGING","TELEMETRY"));
    }
}
