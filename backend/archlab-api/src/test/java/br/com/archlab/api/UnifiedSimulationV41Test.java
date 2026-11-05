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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UnifiedSimulationV41Test {
    @Value("${local.server.port}") int port;
    @Autowired JsonMapper json;
    private final HttpClient client=HttpClient.newHttpClient();

    private HttpResponse<String> post(String route,String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+route))
                .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }
    private static final String MIXED="""
        {"architecture":{"components":[
          {"id":"browser","type":"WEB_BROWSER","displayName":"Browser"},
          {"id":"orders","type":"API","displayName":"Orders","behaviors":[
            {"id":"handle","trigger":"REQUEST_RECEIVED","match":{"operation":"POST /orders"},"actions":[
              {"id":"db","kind":"CALL","connectionId":"orders-db"},
              {"id":"publish","kind":"PUBLISH","connectionId":"orders-broker","destinationName":"orders","dependsOn":["db"]},
              {"id":"respond","kind":"RESPOND","statusCode":201,"dependsOn":["publish"]}]}]},
          {"id":"database","type":"DATABASE","displayName":"DB","processingMs":20},
          {"id":"broker","type":"BROKER","displayName":"Broker","destinations":[{"name":"orders","kind":"QUEUE"}],
           "subscriptions":[{"destinationName":"orders","consumerComponentId":"notification","connectionId":"broker-notification"}]},
          {"id":"notification","type":"API","displayName":"Notification","preProcessingMs":10,"behaviors":[
            {"id":"consume","trigger":"MESSAGE_CONSUMED","match":{"destinationName":"orders"},"actions":[
              {"id":"send","kind":"WS_SEND","sessionAlias":"notification-session","payload":"created","sender":"TARGET"}]}]}
        ],"connections":[
          {"id":"browser-orders","sourceId":"browser","targetId":"orders","latencyMs":2,
           "integration":{"kind":"HTTP_REST","request":{"method":"POST","path":"/orders"},"response":{"statusCode":201}}},
          {"id":"orders-db","sourceId":"orders","targetId":"database","latencyMs":3,"integration":{"kind":"DATABASE_QUERY"}},
          {"id":"orders-broker","sourceId":"orders","targetId":"broker","latencyMs":4,"integration":{"kind":"QUEUE","queueName":"orders"}},
          {"id":"broker-notification","sourceId":"broker","targetId":"notification","latencyMs":6,"integration":{"kind":"QUEUE","queueName":"orders"}},
          {"id":"browser-notification","sourceId":"browser","targetId":"notification","latencyMs":5,
           "integration":{"kind":"WEBSOCKET","path":"/notify","closeMode":"AUTOMATIC"}}
        ]},"scenario":{"entry":{"componentId":"browser","connectionId":"browser-orders","operation":"POST /orders"},
           "sessionSetup":[{"connectionId":"browser-notification","sessionAlias":"notification-session","autoCloseAtEnd":true}]}}
        """;

    @Test void restDatabaseQueueAndWebSocketShareClockAndCausality() throws Exception {
        var validation=post("/api/v1/scenarios/validate",MIXED);
        assertEquals(200,validation.statusCode(),validation.body());
        assertTrue(json.readTree(validation.body()).get("valid").asBoolean(),validation.body());
        var first=post("/api/v1/simulations",MIXED);
        assertEquals(200,first.statusCode(),first.body());
        JsonNode result=json.readTree(first.body());
        assertEquals("COMPLETED",result.get("execution").get("mainStatus").asText());
        assertEquals(201,result.get("execution").get("responseStatusCode").asInt());
        assertEquals("COMPLETED",result.get("execution").get("status").asText());
        assertTrue(result.get("trace").toString().contains("DATABASE_QUERY"));
        assertTrue(result.get("trace").toString().contains("BROKER_DELIVERY"));
        assertTrue(result.get("trace").toString().contains("WEBSOCKET_MESSAGE"));
        assertEquals(1,result.get("metrics").get("messaging").get(0).get("processed").asInt());
        long mainAt=-1, deliveryAt=-1;
        for (JsonNode event:result.get("trace")) if ("STATE_TRANSITION".equals(event.get("type").asText())) {
            if ("REQUEST".equals(event.get("entityKind").asText()) && "COMPLETED".equals(event.get("newState").asText()))
                mainAt=event.get("timestampMs").asLong();
            if ("DELIVERY".equals(event.get("entityKind").asText()) && "COMPLETED".equals(event.get("newState").asText()))
                deliveryAt=event.get("timestampMs").asLong();
        }
        assertTrue(mainAt>=0 && deliveryAt>mainAt,"HTTP response must finish before derived delivery");
        assertEquals(first.body(),post("/api/v1/simulations",MIXED).body(),"Same input must replay identically");
    }

    @Test void missingSessionFailsValidationWithoutImplicitOpen() throws Exception {
        String body=MIXED.replace("\"sessionSetup\":[{\"connectionId\":\"browser-notification\",\"sessionAlias\":\"notification-session\",\"autoCloseAtEnd\":true}]",
                "\"sessionSetup\":[]");
        var response=post("/api/v1/scenarios/validate",body);
        assertEquals(200,response.statusCode());
        assertFalse(json.readTree(response.body()).get("valid").asBoolean());
        assertTrue(response.body().contains("SESSION_NOT_OPEN"));
        assertEquals(422,post("/api/v1/simulations",body).statusCode());
    }

    @Test void cyclicActionsAreRejected() throws Exception {
        String body=MIXED.replace("\"id\":\"db\",\"kind\":\"CALL\",\"connectionId\":\"orders-db\"",
                "\"id\":\"db\",\"kind\":\"CALL\",\"connectionId\":\"orders-db\",\"dependsOn\":[\"respond\"]");
        var response=post("/api/v1/scenarios/validate",body);
        assertFalse(json.readTree(response.body()).get("valid").asBoolean());
        assertTrue(response.body().contains("INVALID_ACTION_DEPENDENCY"));
    }

    @Test void topicAndMqttUseFanoutWithTwoConsumers() throws Exception {
        String twoConsumers=MIXED.replace("{\"destinationName\":\"orders\",\"consumerComponentId\":\"notification\",\"connectionId\":\"broker-notification\"}",
                "{\"destinationName\":\"orders\",\"consumerComponentId\":\"notification\",\"connectionId\":\"broker-notification\"},"+
                "{\"destinationName\":\"orders\",\"consumerComponentId\":\"other\",\"connectionId\":\"broker-other\"}")
                .replace("{\"id\":\"notification\",\"type\":\"API\"",
                        "{\"id\":\"other\",\"type\":\"API\",\"displayName\":\"Other\"},"+
                        "{\"id\":\"notification\",\"type\":\"API\"")
                .replace("{\"id\":\"broker-notification\",\"sourceId\":\"broker\"",
                        "{\"id\":\"broker-other\",\"sourceId\":\"broker\",\"targetId\":\"other\",\"latencyMs\":6,"+
                        "\"integration\":{\"kind\":\"QUEUE\",\"queueName\":\"orders\"}},"+
                        "{\"id\":\"broker-notification\",\"sourceId\":\"broker\"");
        for (String profile : new String[]{"TOPIC","MQTT"}) {
            String body=twoConsumers.replace("\"kind\":\"QUEUE\"","\"kind\":\""+profile+"\"")
                    .replace("\"queueName\":\"orders\"","\"topic\":\"orders\"");
            var response=post("/api/v1/simulations",body);
            assertEquals(200,response.statusCode(),response.body());
            JsonNode result=json.readTree(response.body());
            assertEquals(2,result.get("metrics").get("messaging").get(0).get("processed").asInt());
        }
    }

    @Test void queueWithoutConsumerTerminatesAfterVirtualTimeout() throws Exception {
        String body=MIXED.replace("\"subscriptions\":[{\"destinationName\":\"orders\",\"consumerComponentId\":\"notification\",\"connectionId\":\"broker-notification\"}]",
                "\"subscriptions\":[]");
        var response=post("/api/v1/simulations",body);
        assertEquals(200,response.statusCode(),response.body());
        JsonNode execution=json.readTree(response.body()).get("execution");
        assertEquals("COMPLETED",execution.get("mainStatus").asText());
        assertEquals("COMPLETED_WITH_ERRORS",execution.get("status").asText());
        assertTrue(execution.get("totalDurationMs").asLong()>=30_000);
    }

    @Test void consumerFailureDoesNotUndoCompletedHttpResponse() throws Exception {
        String body=MIXED.replace("{\"id\":\"send\",\"kind\":\"WS_SEND\",\"sessionAlias\":\"notification-session\",\"payload\":\"created\",\"sender\":\"TARGET\"}",
                "{\"id\":\"close\",\"kind\":\"CLOSE_SESSION\",\"sessionAlias\":\"notification-session\"},"+
                "{\"id\":\"send\",\"kind\":\"WS_SEND\",\"sessionAlias\":\"notification-session\",\"payload\":\"created\",\"sender\":\"TARGET\",\"dependsOn\":[\"close\"]}");
        var response=post("/api/v1/simulations",body);
        assertEquals(200,response.statusCode(),response.body());
        JsonNode execution=json.readTree(response.body()).get("execution");
        assertEquals("COMPLETED",execution.get("mainStatus").asText());
        assertEquals("COMPLETED_WITH_ERRORS",execution.get("status").asText());
        assertTrue(response.body().contains("SESSION_NOT_OPEN"));
    }

    @Test void websocketMessageCanCallRestThenReplyOnSameSession() throws Exception {
        String body="""
            {"architecture":{"components":[
              {"id":"client","type":"CLIENT","displayName":"Client"},
              {"id":"socket-api","type":"API","displayName":"Socket API","behaviors":[
                {"id":"on-message","trigger":"WS_MESSAGE_RECEIVED","match":{"websocketPath":"/socket"},"actions":[
                  {"id":"call","kind":"CALL","connectionId":"rest-link"},
                  {"id":"reply","kind":"WS_SEND","sessionAlias":"socket-link","sender":"TARGET","dependsOn":["call"]}]}]},
              {"id":"rest-api","type":"API","displayName":"REST API","preProcessingMs":4,"postProcessingMs":4}
            ],"connections":[
              {"id":"socket-link","sourceId":"client","targetId":"socket-api","latencyMs":2,
               "integration":{"kind":"WEBSOCKET","path":"/socket","closeMode":"AUTOMATIC","messages":[{"sender":"SOURCE","intervalMs":0,"payload":"ping"}]}},
              {"id":"rest-link","sourceId":"socket-api","targetId":"rest-api","latencyMs":3,"integration":{"kind":"HTTP_REST"}}
            ]},"scenario":{"entry":{"componentId":"client","connectionId":"socket-link"}}}
            """;
        var response=post("/api/v1/simulations",body);
        assertEquals(200,response.statusCode(),response.body());
        assertTrue(response.body().contains("API_REQUEST"));
        assertTrue(response.body().contains("WEBSOCKET_MESSAGE"));
        assertEquals("COMPLETED",json.readTree(response.body()).get("execution").get("status").asText());
    }

    @Test void grpcCallToSecondApiUsesTheSameRootExecution() throws Exception {
        String body="""
            {"architecture":{"components":[
              {"id":"client","type":"CLIENT","displayName":"Client"},
              {"id":"api-a","type":"API","displayName":"A","behaviors":[
                {"id":"request","trigger":"REQUEST_RECEIVED","actions":[
                  {"id":"call","kind":"CALL","connectionId":"grpc","timeoutMs":1000},
                  {"id":"respond","kind":"RESPOND","dependsOn":["call"]}]}]},
              {"id":"api-b","type":"API","displayName":"B","preProcessingMs":4,"postProcessingMs":5}
            ],"connections":[
              {"id":"entry","sourceId":"client","targetId":"api-a","latencyMs":2,"integration":{"kind":"HTTP_REST"}},
              {"id":"grpc","sourceId":"api-a","targetId":"api-b","latencyMs":3,
               "integration":{"kind":"GRPC_UNARY","service":"Inventory","method":"Get"}}
            ]},"scenario":{"entry":{"componentId":"client","connectionId":"entry"}}}
            """;
        var response=post("/api/v1/simulations",body);
        assertEquals(200,response.statusCode(),response.body());
        JsonNode result=json.readTree(response.body());
        assertEquals("COMPLETED",result.get("execution").get("status").asText());
        assertTrue(result.get("execution").get("totalDurationMs").asLong()<1000);
        assertTrue(result.get("metrics").get("communicationByConnection").toString().contains("grpc"));
    }

    @Test void disconnectedOutgoingConnectionIsNotExecutedWithoutAction() throws Exception {
        String body="""
            {"architecture":{"components":[
              {"id":"client","type":"CLIENT","displayName":"Client"},
              {"id":"api","type":"API","displayName":"API"},
              {"id":"db","type":"DATABASE","displayName":"DB","processingMs":100}
            ],"connections":[
              {"id":"entry","sourceId":"client","targetId":"api","latencyMs":2,"integration":{"kind":"HTTP_REST"}},
              {"id":"unused","sourceId":"api","targetId":"db","latencyMs":3,"integration":{"kind":"DATABASE_QUERY"}}
            ]},"scenario":{"entry":{"componentId":"client","connectionId":"entry"}}}
            """;
        var response=post("/api/v1/simulations",body);
        assertEquals(200,response.statusCode(),response.body());
        assertFalse(response.body().contains("DATABASE_QUERY"));
        assertEquals(4,json.readTree(response.body()).get("execution").get("totalDurationMs").asInt());
    }

    @Test void architectureValidationRejectsAmbiguousMatchersAndWrongConnection() throws Exception {
        JsonNode root=json.readTree(MIXED);
        String architecture=root.get("architecture").toString();
        String ambiguous=architecture.replace("{\"id\":\"handle\",\"trigger\":\"REQUEST_RECEIVED\",\"match\":{\"operation\":\"POST /orders\"},\"actions\":[",
                "{\"id\":\"generic\",\"trigger\":\"REQUEST_RECEIVED\",\"actions\":[{\"id\":\"respond-generic\",\"kind\":\"RESPOND\"}]},"+
                "{\"id\":\"handle\",\"trigger\":\"REQUEST_RECEIVED\",\"match\":{\"operation\":\"POST /orders\"},\"actions\":[");
        var response=post("/api/v1/architectures/validate",ambiguous);
        assertEquals(200,response.statusCode(),response.body());
        assertTrue(response.body().contains("AMBIGUOUS_BEHAVIOR_MATCH"));
        String wrong=architecture.replace("\"connectionId\":\"orders-db\"","\"connectionId\":\"missing\"");
        response=post("/api/v1/architectures/validate",wrong);
        assertEquals(200,response.statusCode(),response.body());
        assertTrue(response.body().contains("INVALID_ACTION_CONNECTION"));
    }

    @Test void malformedUnifiedActionReturnsTypedFourHundred() throws Exception {
        String body=MIXED.replace("\"kind\":\"CALL\"","\"kind\":\"UNKNOWN\"");
        var response=post("/api/v1/simulations",body);
        assertEquals(400,response.statusCode());
        assertEquals("INVALID_REQUEST",json.readTree(response.body()).get("code").asText());
    }

    @Test void forkJoinWaitsForBothExplicitBranches() throws Exception {
        String body="""
            {"architecture":{"components":[
              {"id":"client","type":"CLIENT","displayName":"Client"},
              {"id":"api","type":"API","displayName":"API","behaviors":[
                {"id":"on-request","trigger":"REQUEST_RECEIVED","actions":[
                  {"id":"fork","kind":"FORK","actionIds":["local","call"]},
                  {"id":"local","kind":"PROCESS","durationMs":20,"dependsOn":["fork"]},
                  {"id":"call","kind":"CALL","connectionId":"db-link","dependsOn":["fork"]},
                  {"id":"join","kind":"JOIN","actionIds":["local","call"]},
                  {"id":"respond","kind":"RESPOND","dependsOn":["join"]}]}]},
              {"id":"db","type":"DATABASE","displayName":"DB","processingMs":10}
            ],"connections":[
              {"id":"entry","sourceId":"client","targetId":"api","latencyMs":1,"integration":{"kind":"HTTP_REST"}},
              {"id":"db-link","sourceId":"api","targetId":"db","latencyMs":1,"integration":{"kind":"DATABASE_QUERY"}}
            ]},"scenario":{"entry":{"componentId":"client","connectionId":"entry"}}}
            """;
        var response=post("/api/v1/simulations",body);
        assertEquals(200,response.statusCode(),response.body());
        JsonNode result=json.readTree(response.body());
        assertEquals(22,result.get("execution").get("totalDurationMs").asInt());
        assertTrue(response.body().contains("\"actionId\":\"join\""));
    }
}
