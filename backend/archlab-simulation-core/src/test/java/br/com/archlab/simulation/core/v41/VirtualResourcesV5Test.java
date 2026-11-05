package br.com.archlab.simulation.core.v41;

import br.com.archlab.simulation.core.v2.*;
import br.com.archlab.simulation.core.v41.BehaviorPlanV41.Action;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VirtualResourcesV5Test {
    private static ServiceComponentV5 resource(ComponentTypeV2 type,long ttl) {
        return new ServiceComponentV5(type.name(),type.name(),type,0,0,ttl,
                List.of(),List.of(),List.of(),"REVIEW");
    }
    private static Action action(String kind,Map<String,Object> options) {
        return new Action("a",kind,List.of(),"edge",null,null,null,null,0,0,200,List.of(),options);
    }
    @Test void cacheUsesVirtualTimeAndIsolatedRunState() {
        var cache=resource(ComponentTypeV2.CACHE,10);
        var get=action("CACHE_GET",Map.of("key","k"));
        var put=action("CACHE_PUT",Map.of("key","k","valueMetadata","v"));
        var first=new VirtualResourcesV5();
        assertEquals("MISS",first.execute(cache,get,0,Map.of()).resultCode());
        assertEquals("STORED",first.execute(cache,put,5,Map.of()).resultCode());
        assertEquals("HIT",first.execute(cache,get,14,Map.of()).resultCode());
        assertEquals("MISS",first.execute(cache,get,15,Map.of()).resultCode());
        first.execute(cache,put,20,Map.of());
        assertEquals("REMOVED",first.execute(cache,action("CACHE_INVALIDATE",Map.of("key","k")),21,Map.of()).resultCode());
        assertEquals("MISS",first.execute(cache,get,22,Map.of()).resultCode());
        assertEquals("MISS",new VirtualResourcesV5().execute(cache,get,6,Map.of()).resultCode());
    }
    @Test void storageSearchRegistryAndRulesAreDeterministic() {
        var state=new VirtualResourcesV5();
        var storage=resource(ComponentTypeV2.OBJECT_STORAGE,0);
        assertEquals("CREATED",state.execute(storage,action("OBJECT_PUT",Map.of("objectKey","x","sizeBytes",8L,
                "metadata",Map.of("owner","alias"))),0,Map.of()).resultCode());
        var object=state.execute(storage,action("OBJECT_GET",Map.of("objectKey","x")),1,Map.of());
        assertEquals("FOUND",object.resultCode());
        assertTrue(object.details().get("metadata").toString().contains("owner=alias"));
        assertEquals("DELETED",state.execute(storage,action("OBJECT_DELETE",Map.of("objectKey","x")),2,Map.of()).resultCode());
        assertEquals("NOT_FOUND",state.execute(storage,action("OBJECT_GET",Map.of("objectKey","x")),3,Map.of()).resultCode());
        var search=resource(ComponentTypeV2.SEARCH_ENGINE,0);
        state.execute(search,action("INDEX_DOCUMENT",Map.of("indexName","i","documentId","b","tags",Map.of("kind","order"))),0,Map.of());
        state.execute(search,action("INDEX_DOCUMENT",Map.of("indexName","i","documentId","a","tags",Map.of("kind","order"))),0,Map.of());
        assertEquals("a,b",state.execute(search,action("SEARCH_DOCUMENTS",Map.of("indexName","i","tags",Map.of("kind","order"))),1,Map.of()).details().get("documentIds"));
        var registry=resource(ComponentTypeV2.SERVICE_REGISTRY,0);
        assertEquals("UNAVAILABLE",state.execute(registry,action("DISCOVER_SERVICE",Map.of("serviceName","orders")),0,Map.of()).resultCode());
        state.execute(registry,action("REGISTER_SERVICE",Map.of("serviceName","orders","instanceId","one","targetComponentId","service")),0,Map.of());
        assertEquals("FOUND",state.execute(registry,action("DISCOVER_SERVICE",Map.of("serviceName","orders")),1,Map.of()).resultCode());
        state.execute(registry,action("UNREGISTER_SERVICE",Map.of("serviceName","orders","instanceId","one")),2,Map.of());
        assertEquals("UNAVAILABLE",state.execute(registry,action("DISCOVER_SERVICE",Map.of("serviceName","orders")),3,Map.of()).resultCode());
        state.execute(registry,action("REGISTER_SERVICE",Map.of("serviceName","orders","instanceId","two",
                "targetComponentId","service","instanceStatus","INACTIVE")),4,Map.of());
        assertEquals("UNAVAILABLE",state.execute(registry,action("DISCOVER_SERVICE",Map.of("serviceName","orders")),5,Map.of()).resultCode());
        var rules=new ServiceComponentV5("rules","Rules",ComponentTypeV2.RULES_ENGINE,0,0,0,
                List.of(),List.of(),List.of(
                    new ServiceComponentV5.Rule("allow-low","status","EQ","DENY",1,"ALLOW"),
                    new ServiceComponentV5.Rule("deny","status","EQ","DENY",10,"DENY"),
                    new ServiceComponentV5.Rule("review-tie","status","EQ","DENY",10,"REVIEW")),"REVIEW");
        assertEquals("DENY",state.execute(rules,action("EVALUATE_RULE",Map.of()),0,Map.of("status","DENY")).resultCode());
        assertEquals("REVIEW",state.execute(rules,action("EVALUATE_RULE",Map.of()),0,Map.of()).resultCode());
    }
    @Test void notificationCanSimulateSuccessAndFailureWithoutExternalEffects() {
        var state=new VirtualResourcesV5();
        var notifications=resource(ComponentTypeV2.NOTIFICATION_SERVICE,0);
        var fields=Map.<String,Object>of("channel","EMAIL","recipientAlias","alias");
        assertEquals("SENT",state.execute(notifications,action("SEND_NOTIFICATION",fields),0,Map.of()).resultCode());
        var failed=new HashMap<>(fields);
        failed.put("simulatedResult","FAILED");
        assertEquals("FAILED",state.execute(notifications,action("SEND_NOTIFICATION",failed),1,Map.of()).resultCode());
    }
}
