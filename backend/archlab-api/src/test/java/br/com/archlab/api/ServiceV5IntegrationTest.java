package br.com.archlab.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServiceV5IntegrationTest {
    @Value("${local.server.port}") int port;
    @Autowired JsonMapper json;
    private final HttpClient client=HttpClient.newHttpClient();
    private HttpResponse<String> post(String endpoint,String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+endpoint))
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString());
    }
    private static final String CACHE_MISS="""
        {"architecture":{"components":[
          {"id":"browser","type":"WEB_BROWSER","displayName":"Browser"},
          {"id":"orders","type":"SERVICE","displayName":"Orders","behaviors":[
            {"id":"read","trigger":"REQUEST_RECEIVED","match":{"operation":"GET /orders"},"actions":[
              {"id":"get","kind":"CACHE_GET","connectionId":"to-cache","key":"order-1","outputRef":"cache-result"},
              {"id":"branch","kind":"BRANCH","dependsOn":["get"],"resultRef":"cache-result",
               "cases":[{"equals":"HIT","actionIds":["respond-hit"]},
                        {"equals":"MISS","actionIds":["db","respond-miss"]}]},
              {"id":"db","kind":"CALL","connectionId":"to-db","dependsOn":["branch"]},
              {"id":"respond-miss","kind":"RESPOND","dependsOn":["db"],"statusCode":200},
              {"id":"respond-hit","kind":"RESPOND","dependsOn":["branch"],"statusCode":204}]}]},
          {"id":"cache","type":"CACHE","displayName":"Cache","defaultTtlMs":100},
          {"id":"db","type":"DATABASE","displayName":"Database","processingMs":2}
        ],"connections":[
          {"id":"in","sourceId":"browser","targetId":"orders","latencyMs":1,
           "integration":{"kind":"HTTP_REST","request":{"method":"GET","path":"/orders"}}},
          {"id":"to-cache","sourceId":"orders","targetId":"cache","latencyMs":1,
           "integration":{"kind":"HTTP_REST","request":{"method":"GET","path":"/cache"}}},
          {"id":"to-db","sourceId":"orders","targetId":"db","latencyMs":1,
           "integration":{"kind":"DATABASE_QUERY"}}
        ]},"scenario":{"entry":{"componentId":"browser","connectionId":"in","operation":"GET /orders"}}}
        """;

    @Test void cacheMissSelectsOnlyDatabaseBranchAndReplaysIdentically() throws Exception {
        var validation=post("/api/v1/scenarios/validate",CACHE_MISS);
        assertEquals(200,validation.statusCode(),validation.body());
        assertTrue(json.readTree(validation.body()).get("valid").asBoolean(),validation.body());
        var first=post("/api/v1/simulations",CACHE_MISS);
        assertEquals(200,first.statusCode(),first.body());
        JsonNode result=json.readTree(first.body());
        assertEquals("MISS",result.get("resourceOutcomes").get(0).get("resultCode").asText());
        assertEquals(200,result.get("execution").get("responseStatusCode").asInt());
        assertTrue(result.get("trace").toString().contains("BRANCH_NOT_SELECTED"));
        assertEquals(first.body(),post("/api/v1/simulations",CACHE_MISS).body());
    }
    @Test void rejectsBranchWithoutPredecessorResultAndWrongResourceConnection() throws Exception {
        String missing=CACHE_MISS.replace("\"resultRef\":\"cache-result\",",
                "\"resultRef\":\"missing\",");
        JsonNode invalidRef=json.readTree(post("/api/v1/scenarios/validate",missing).body());
        assertFalse(invalidRef.get("valid").asBoolean());
        assertTrue(invalidRef.get("violations").toString().contains("INVALID_ACTION_DEPENDENCY"));
        String wrong=CACHE_MISS.replace("\"kind\":\"CACHE_GET\",\"connectionId\":\"to-cache\"",
                "\"kind\":\"CACHE_GET\",\"connectionId\":\"to-db\"");
        JsonNode invalidConnection=json.readTree(post("/api/v1/scenarios/validate",wrong).body());
        assertFalse(invalidConnection.get("valid").asBoolean());
        assertTrue(invalidConnection.get("violations").toString().contains("INVALID_RESOURCE_CONNECTION"));
    }
    private static final String GATEWAY_BFF="""
        {"architecture":{"components":[
          {"id":"browser","type":"WEB_BROWSER","displayName":"Browser"},
          {"id":"gateway","type":"API_GATEWAY","displayName":"Gateway",
           "routes":[{"operation":"GET /home","connectionId":"gateway-bff"}],
           "behaviors":[{"id":"route","trigger":"REQUEST_RECEIVED","match":{"operation":"GET /home"},
             "actions":[{"id":"forward","kind":"CALL","connectionId":"gateway-bff"},
                        {"id":"response","kind":"RESPOND","dependsOn":["forward"],"statusCode":200}]}]},
          {"id":"bff","type":"BFF","displayName":"BFF","behaviors":[
            {"id":"compose","trigger":"REQUEST_RECEIVED","actions":[
              {"id":"first","kind":"CALL","connectionId":"bff-a"},
              {"id":"second","kind":"CALL","connectionId":"bff-b","dependsOn":["first"]},
              {"id":"response","kind":"RESPOND","dependsOn":["second"],"statusCode":200}]}]},
          {"id":"a","type":"SERVICE","displayName":"A","behaviors":[
            {"id":"handle","trigger":"REQUEST_RECEIVED","actions":[{"id":"work","kind":"PROCESS","durationMs":2},
              {"id":"response","kind":"RESPOND","dependsOn":["work"],"statusCode":200}]}]},
          {"id":"b","type":"SERVICE","displayName":"B","behaviors":[
            {"id":"handle","trigger":"REQUEST_RECEIVED","actions":[{"id":"work","kind":"PROCESS","durationMs":3},
              {"id":"response","kind":"RESPOND","dependsOn":["work"],"statusCode":200}]}]}
        ],"connections":[
          {"id":"browser-gateway","sourceId":"browser","targetId":"gateway","latencyMs":1,
           "integration":{"kind":"HTTP_REST","request":{"method":"GET","path":"/home"}}},
          {"id":"gateway-bff","sourceId":"gateway","targetId":"bff","latencyMs":1,
           "integration":{"kind":"HTTP_REST","request":{"method":"GET","path":"/home"}}},
          {"id":"bff-a","sourceId":"bff","targetId":"a","latencyMs":1,
           "integration":{"kind":"HTTP_REST","request":{"method":"GET","path":"/a"}}},
          {"id":"bff-b","sourceId":"bff","targetId":"b","latencyMs":1,
           "integration":{"kind":"HTTP_REST","request":{"method":"GET","path":"/b"}}}
        ]},"scenario":{"entry":{"componentId":"browser","connectionId":"browser-gateway","operation":"GET /home"}}}
        """;

    @Test void gatewayRoutesToBffWhichComposesExplicitCalls() throws Exception {
        var validation=post("/api/v1/scenarios/validate",GATEWAY_BFF);
        assertTrue(json.readTree(validation.body()).get("valid").asBoolean(),validation.body());
        var result=post("/api/v1/simulations",GATEWAY_BFF);
        assertEquals(200,result.statusCode(),result.body());
        JsonNode body=json.readTree(result.body());
        assertEquals("COMPLETED",body.get("execution").get("mainStatus").asText());
        assertEquals(4,body.get("metrics").get("exchanges").size());
        assertEquals(result.body(),post("/api/v1/simulations",GATEWAY_BFF).body());
    }
    private static final String WORKER_NOTIFICATION="""
        {"architecture":{"components":[
          {"id":"browser","type":"WEB_BROWSER","displayName":"Browser"},
          {"id":"orders","type":"SERVICE","displayName":"Orders","behaviors":[
            {"id":"accept","trigger":"REQUEST_RECEIVED","actions":[
              {"id":"publish","kind":"PUBLISH","connectionId":"to-broker","destinationName":"created"},
              {"id":"respond","kind":"RESPOND","dependsOn":["publish"],"statusCode":202}]}]},
          {"id":"broker","type":"BROKER","displayName":"Broker",
           "destinations":[{"name":"created","kind":"QUEUE"}],
           "subscriptions":[{"destinationName":"created","consumerComponentId":"worker","connectionId":"to-worker"}]},
          {"id":"worker","type":"WORKER","displayName":"Worker","behaviors":[
            {"id":"consume","trigger":"MESSAGE_CONSUMED","match":{"destinationName":"created"},
             "actions":[{"id":"notify","kind":"SEND_NOTIFICATION","connectionId":"to-notify",
               "channel":"EMAIL","recipientAlias":"customer-1","outputRef":"notification-result"}]}]},
          {"id":"notifications","type":"NOTIFICATION_SERVICE","displayName":"Notifications"}
        ],"connections":[
          {"id":"in","sourceId":"browser","targetId":"orders","latencyMs":1,
           "integration":{"kind":"HTTP_REST","request":{"method":"POST","path":"/orders"}}},
          {"id":"to-broker","sourceId":"orders","targetId":"broker","latencyMs":1,
           "integration":{"kind":"QUEUE","queueName":"created"}},
          {"id":"to-worker","sourceId":"broker","targetId":"worker","latencyMs":1,
           "integration":{"kind":"QUEUE","queueName":"created"}},
          {"id":"to-notify","sourceId":"worker","targetId":"notifications","latencyMs":2,
           "integration":{"kind":"HTTP_REST","request":{"method":"POST","path":"/notify"}}}
        ]},"scenario":{"entry":{"componentId":"browser","connectionId":"in","operation":"POST /orders"}}}
        """;

    @Test void brokerWorkerAndNotificationPreserveCausalityAndMainResponse() throws Exception {
        var validation=post("/api/v1/scenarios/validate",WORKER_NOTIFICATION);
        assertTrue(json.readTree(validation.body()).get("valid").asBoolean(),validation.body());
        var response=post("/api/v1/simulations",WORKER_NOTIFICATION);
        assertEquals(200,response.statusCode(),response.body());
        JsonNode result=json.readTree(response.body());
        assertEquals("SENT",result.get("resourceOutcomes").get(0).get("resultCode").asText());
        assertEquals(202,result.get("execution").get("responseStatusCode").asInt());
        assertTrue(result.get("trace").toString().contains("BROKER_DELIVERY"));
        assertEquals(1,result.get("metrics").get("messaging").get(0).get("processed").asInt());
    }

    @Test void notificationFailureIsObservableAfterMainResponse() throws Exception {
        String failure=WORKER_NOTIFICATION.replace("\"outputRef\":\"notification-result\"",
                "\"outputRef\":\"notification-result\",\"simulatedResult\":\"FAILED\"");
        var response=post("/api/v1/simulations",failure);
        assertEquals(200,response.statusCode(),response.body());
        JsonNode result=json.readTree(response.body());
        assertEquals("FAILED",result.get("resourceOutcomes").get(0).get("status").asText());
        assertEquals("COMPLETED",result.get("execution").get("mainStatus").asText());
        assertEquals("COMPLETED_WITH_ERRORS",result.get("execution").get("status").asText());
    }
    @Test void notificationTimeoutUsesVirtualDeadline() throws Exception {
        String timeout=WORKER_NOTIFICATION.replace("\"outputRef\":\"notification-result\"",
                "\"outputRef\":\"notification-result\",\"durationMs\":10,\"timeoutMs\":2");
        var response=post("/api/v1/simulations",timeout);
        assertEquals(200,response.statusCode(),response.body());
        JsonNode result=json.readTree(response.body());
        assertEquals("TIMED_OUT",result.get("resourceOutcomes").get(0).get("status").asText());
        assertEquals("COMPLETED",result.get("execution").get("mainStatus").asText());
        assertEquals("COMPLETED_WITH_ERRORS",result.get("execution").get("status").asText());
    }
    private static final String AUTH_RULES="""
        {"architecture":{"components":[
          {"id":"browser","type":"WEB_BROWSER","displayName":"Browser"},
          {"id":"auth","type":"AUTHENTICATION_SERVICE","displayName":"Auth","behaviors":[
            {"id":"check","trigger":"REQUEST_RECEIVED","actions":[
              {"id":"evaluate","kind":"EVALUATE_RULE","connectionId":"auth-rules","outputRef":"decision"},
              {"id":"branch","kind":"BRANCH","dependsOn":["evaluate"],"resultRef":"decision",
               "cases":[{"equals":"ALLOW","actionIds":["allow"]},
                        {"equals":"DENY","actionIds":["deny"]}]},
              {"id":"allow","kind":"RESPOND","dependsOn":["branch"],"statusCode":200},
              {"id":"deny","kind":"RESPOND","dependsOn":["branch"],"statusCode":403}]}]},
          {"id":"rules","type":"RULES_ENGINE","displayName":"Rules","defaultDecision":"DENY"}
        ],"connections":[
          {"id":"in","sourceId":"browser","targetId":"auth","latencyMs":1,
           "integration":{"kind":"HTTP_REST","request":{"method":"GET","path":"/secure"}}},
          {"id":"auth-rules","sourceId":"auth","targetId":"rules","latencyMs":1,
           "integration":{"kind":"HTTP_REST","request":{"method":"POST","path":"/decide"}}}
        ]},"scenario":{"entry":{"componentId":"browser","connectionId":"in","operation":"GET /secure"}}}
        """;

    @Test void authenticationUsesTypedRulesDecisionAndExclusiveResponse() throws Exception {
        var validation=post("/api/v1/scenarios/validate",AUTH_RULES);
        assertTrue(json.readTree(validation.body()).get("valid").asBoolean(),validation.body());
        var response=post("/api/v1/simulations",AUTH_RULES);
        assertEquals(200,response.statusCode(),response.body());
        JsonNode result=json.readTree(response.body());
        assertEquals("DENY",result.get("resourceOutcomes").get(0).get("resultCode").asText());
        assertEquals(403,result.get("execution").get("responseStatusCode").asInt());
        assertTrue(result.get("trace").toString().contains("BRANCH_NOT_SELECTED"));
    }
    private static final String RESOURCES="""
        {"architecture":{"components":[
          {"id":"client","type":"WEB_BROWSER","displayName":"Client"},
          {"id":"service","type":"SERVICE","displayName":"Service","behaviors":[
            {"id":"run","trigger":"REQUEST_RECEIVED","actions":[
              {"id":"cache-put","kind":"CACHE_PUT","connectionId":"cache-edge","key":"k","valueMetadata":"v","ttlMs":100},
              {"id":"cache-get","kind":"CACHE_GET","connectionId":"cache-edge","key":"k","dependsOn":["cache-put"]},
              {"id":"object-put","kind":"OBJECT_PUT","connectionId":"object-edge","objectKey":"o","sizeBytes":8,"dependsOn":["cache-get"]},
              {"id":"object-get","kind":"OBJECT_GET","connectionId":"object-edge","objectKey":"o","dependsOn":["object-put"]},
              {"id":"object-delete","kind":"OBJECT_DELETE","connectionId":"object-edge","objectKey":"o","dependsOn":["object-get"]},
              {"id":"index","kind":"INDEX_DOCUMENT","connectionId":"search-edge","indexName":"i","documentId":"d","tags":{"type":"order"},"dependsOn":["object-delete"]},
              {"id":"search","kind":"SEARCH_DOCUMENTS","connectionId":"search-edge","indexName":"i","tags":{"type":"order"},"dependsOn":["index"]},
              {"id":"register","kind":"REGISTER_SERVICE","connectionId":"registry-edge","serviceName":"orders","instanceId":"one","targetComponentId":"service","dependsOn":["search"]},
              {"id":"discover","kind":"DISCOVER_SERVICE","connectionId":"registry-edge","serviceName":"orders","dependsOn":["register"]},
              {"id":"unregister","kind":"UNREGISTER_SERVICE","connectionId":"registry-edge","serviceName":"orders","instanceId":"one","dependsOn":["discover"]},
              {"id":"respond","kind":"RESPOND","dependsOn":["unregister"],"statusCode":200}]}]},
          {"id":"cache","type":"CACHE","displayName":"Cache"},
          {"id":"objects","type":"OBJECT_STORAGE","displayName":"Objects"},
          {"id":"search","type":"SEARCH_ENGINE","displayName":"Search"},
          {"id":"registry","type":"SERVICE_REGISTRY","displayName":"Registry"}
        ],"connections":[
          {"id":"in","sourceId":"client","targetId":"service","latencyMs":1,"integration":{"kind":"HTTP_REST","request":{"method":"GET","path":"/run"}}},
          {"id":"cache-edge","sourceId":"service","targetId":"cache","latencyMs":1,"integration":{"kind":"HTTP_REST","request":{"method":"GET","path":"/cache"}}},
          {"id":"object-edge","sourceId":"service","targetId":"objects","latencyMs":1,"integration":{"kind":"HTTP_REST","request":{"method":"GET","path":"/objects"}}},
          {"id":"search-edge","sourceId":"service","targetId":"search","latencyMs":1,"integration":{"kind":"HTTP_REST","request":{"method":"GET","path":"/search"}}},
          {"id":"registry-edge","sourceId":"service","targetId":"registry","latencyMs":1,"integration":{"kind":"HTTP_REST","request":{"method":"GET","path":"/registry"}}}
        ]},"scenario":{"entry":{"componentId":"client","connectionId":"in","operation":"GET /run"}}}
        """;

    @Test void resourceActionsShareOneEngineAndIsolatedState() throws Exception {
        var validation=post("/api/v1/scenarios/validate",RESOURCES);
        assertTrue(json.readTree(validation.body()).get("valid").asBoolean(),validation.body());
        var response=post("/api/v1/simulations",RESOURCES);
        assertEquals(200,response.statusCode(),response.body());
        JsonNode outcomes=json.readTree(response.body()).get("resourceOutcomes");
        assertEquals(10,outcomes.size());
        assertEquals("HIT",outcomes.get(1).get("resultCode").asText());
        assertEquals("FOUND",outcomes.get(3).get("resultCode").asText());
        assertEquals("DELETED",outcomes.get(4).get("resultCode").asText());
        assertEquals("FOUND",outcomes.get(8).get("resultCode").asText());
        assertEquals(response.body(),post("/api/v1/simulations",RESOURCES).body());
    }
    private static final String WEBHOOK_ORCHESTRATOR_BUS="""
        {"architecture":{"components":[
          {"id":"client","type":"WEB_BROWSER","displayName":"Client"},
          {"id":"webhook","type":"WEBHOOK_RECEIVER","displayName":"Webhook","behaviors":[
            {"id":"receive","trigger":"REQUEST_RECEIVED","actions":[
              {"id":"forward","kind":"CALL","connectionId":"webhook-orchestrator"},
              {"id":"respond","kind":"RESPOND","dependsOn":["forward"],"statusCode":202}]}]},
          {"id":"orchestrator","type":"ORCHESTRATOR_SERVICE","displayName":"Orchestrator","behaviors":[
            {"id":"coordinate","trigger":"REQUEST_RECEIVED","actions":[
              {"id":"bus","kind":"CALL","connectionId":"orchestrator-bus"},
              {"id":"respond","kind":"RESPOND","dependsOn":["bus"],"statusCode":200}]}]},
          {"id":"bus","type":"INTEGRATION_BUS","displayName":"ESB","behaviors":[
            {"id":"transform","trigger":"REQUEST_RECEIVED","actions":[
              {"id":"transform","kind":"TRANSFORM","connectionId":"bus-service","outputRef":"mapped","metadata":{"format":"normalized"}},
              {"id":"call","kind":"CALL","connectionId":"bus-service","dependsOn":["transform"]},
              {"id":"respond","kind":"RESPOND","dependsOn":["call"],"statusCode":200}]}]},
          {"id":"service","type":"SERVICE","displayName":"Service","behaviors":[
            {"id":"handle","trigger":"REQUEST_RECEIVED","actions":[
              {"id":"respond","kind":"RESPOND","statusCode":200}]}]}
        ],"connections":[
          {"id":"in","sourceId":"client","targetId":"webhook","latencyMs":1,"integration":{"kind":"HTTP_REST","request":{"method":"POST","path":"/webhook"}}},
          {"id":"webhook-orchestrator","sourceId":"webhook","targetId":"orchestrator","latencyMs":1,"integration":{"kind":"HTTP_REST","request":{"method":"POST","path":"/coordinate"}}},
          {"id":"orchestrator-bus","sourceId":"orchestrator","targetId":"bus","latencyMs":1,"integration":{"kind":"HTTP_REST","request":{"method":"POST","path":"/bus"}}},
          {"id":"bus-service","sourceId":"bus","targetId":"service","latencyMs":1,"integration":{"kind":"HTTP_REST","request":{"method":"POST","path":"/service"}}}
        ]},"scenario":{"entry":{"componentId":"client","connectionId":"in","operation":"POST /webhook"}}}
        """;

    @Test void webhookOrchestratorAndEsbPreserveCausalTrace() throws Exception {
        var validation=post("/api/v1/scenarios/validate",WEBHOOK_ORCHESTRATOR_BUS);
        assertTrue(json.readTree(validation.body()).get("valid").asBoolean(),validation.body());
        var response=post("/api/v1/simulations",WEBHOOK_ORCHESTRATOR_BUS);
        assertEquals(200,response.statusCode(),response.body());
        JsonNode result=json.readTree(response.body());
        assertEquals("TRANSFORMED",result.get("resourceOutcomes").get(0).get("resultCode").asText());
        assertEquals(4,result.get("metrics").get("exchanges").size());
        assertTrue(result.get("trace").toString().contains("VIRTUAL_TRANSFORM"));
    }
}
