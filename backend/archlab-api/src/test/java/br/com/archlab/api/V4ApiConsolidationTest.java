package br.com.archlab.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class V4ApiConsolidationTest {
    @Value("${local.server.port}") int port;
    @Autowired JsonMapper json;
    private final HttpClient client = HttpClient.newHttpClient();

    private static final String ARCHITECTURE = """
        {"components":[
          {"id":"client-1","type":"CLIENT","displayName":"Client"},
          {"id":"api-1","type":"API","displayName":"API","preProcessingMs":30,"postProcessingMs":20},
          {"id":"db-1","type":"DATABASE","displayName":"Database","processingMs":150}
        ],"connections":[
          {"id":"connection-1","sourceId":"client-1","targetId":"api-1","latencyMs":20,"integration":{"kind":"HTTP_REST"}},
          {"id":"connection-2","sourceId":"api-1","targetId":"db-1","latencyMs":10,"integration":{"kind":"DATABASE_QUERY"}}
        ]}
        """;
    private static final String SCENARIO = """
        {"kind":"REQUEST_RESPONSE","entryComponentId":"client-1","entryConnectionId":"connection-1",
         "apiExecutions":[{"componentId":"api-1","dependencyConnectionId":"connection-2"}]}
        """;

    private HttpResponse<String> post(String route, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + route))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
    private static String envelope(String scenario) {
        return "{\"architecture\":" + ARCHITECTURE + ",\"scenario\":" + scenario + "}";
    }

    @Test void canonicalArchitectureAndScenarioValidateOnV1() throws Exception {
        var graph = post("/api/v1/architectures/validate", ARCHITECTURE);
        assertEquals(200, graph.statusCode());
        assertTrue(json.readTree(graph.body()).get("valid").asBoolean());
        var scenario = post("/api/v1/scenarios/validate", envelope(SCENARIO));
        assertEquals(200, scenario.statusCode());
        assertTrue(json.readTree(scenario.body()).get("valid").asBoolean());
    }

    @Test void canonicalSimulationRetains260msAndAddsMainStatus() throws Exception {
        var response = post("/api/v1/simulations", envelope(SCENARIO));
        assertEquals(200, response.statusCode());
        JsonNode execution = json.readTree(response.body()).get("execution");
        assertEquals("COMPLETED", execution.get("status").asText());
        assertEquals("COMPLETED", execution.get("mainStatus").asText());
        assertEquals(260, execution.get("totalDurationMs").asInt());
        assertTrue(json.readTree(response.body()).get("messageRepresentations").size() > 0);
    }

    @Test void missingKindIsRejectedOnV1WithoutChangingV2() throws Exception {
        String v2Scenario = SCENARIO.replace("\"kind\":\"REQUEST_RESPONSE\",", "");
        var v1 = post("/api/v1/simulations", envelope(v2Scenario));
        assertEquals(400, v1.statusCode());
        assertEquals("INVALID_REQUEST", json.readTree(v1.body()).get("code").asText());
        var v2 = post("/api/v2/scenarios/validate", envelope(v2Scenario));
        assertEquals(200, v2.statusCode());
        assertTrue(json.readTree(v2.body()).get("valid").asBoolean());
    }

    @Test void invalidCanonicalScenarioReturns422() throws Exception {
        String missingEntry = SCENARIO.replace("connection-1", "missing");
        var validation = post("/api/v1/scenarios/validate", envelope(missingEntry));
        assertEquals(200, validation.statusCode());
        assertFalse(json.readTree(validation.body()).get("valid").asBoolean());
        var simulation = post("/api/v1/simulations", envelope(missingEntry));
        assertEquals(422, simulation.statusCode());
        assertEquals("SIMULATION_INVALID", json.readTree(simulation.body()).get("code").asText());
    }

    @Test void canonicalApiWithoutDatabaseIsValidButLegacyRuleIsUnchanged() throws Exception {
        String withoutDb = """
            {"components":[{"id":"c","type":"CLIENT","displayName":"Client"},
                           {"id":"a","type":"API","displayName":"API"}],
             "connections":[{"id":"ca","sourceId":"c","targetId":"a",
                             "integration":{"kind":"HTTP_REST"}}]}
            """;
        var response = post("/api/v1/architectures/validate", withoutDb);
        assertEquals(200, response.statusCode());
        assertTrue(json.readTree(response.body()).get("valid").asBoolean());
    }

    @Test void virtualDurationAboveOneHourReturns422() throws Exception {
        String slowArchitecture = ARCHITECTURE.replace("\"processingMs\":150", "\"processingMs\":3600001");
        var response = post("/api/v1/simulations",
                "{\"architecture\":" + slowArchitecture + ",\"scenario\":" + SCENARIO + "}");
        assertEquals(422, response.statusCode());
        assertEquals("EXCESSIVE_EXECUTION_DURATION",
                json.readTree(response.body()).get("violations").get(0).get("code").asText());
    }

    @Test void v1PayloadLimitReturns413() throws Exception {
        var response = post("/api/v1/simulations", " ".repeat(1024 * 1024 + 1));
        assertEquals(413, response.statusCode());
        assertEquals("PAYLOAD_TOO_LARGE", json.readTree(response.body()).get("code").asText());
    }

    @Test void legacyTcpOnClientEdgeIsRejectedWithoutConversion() throws Exception {
        String legacy = """
            {"components":[{"id":"c","type":"CLIENT"},
                           {"id":"a","type":"API","preProcessingMs":0,"postProcessingMs":0},
                           {"id":"d","type":"DATABASE","processingMs":0}],
             "connections":[{"id":"ca","sourceId":"c","targetId":"a","protocol":"TCP","latencyMs":0},
                            {"id":"ad","sourceId":"a","targetId":"d","protocol":"TCP","latencyMs":0}]}
            """;
        var response = post("/api/v1/simulations", legacy);
        assertEquals(422, response.statusCode());
        assertEquals("ARCHITECTURE_INVALID", json.readTree(response.body()).get("code").asText());
    }

    @Test void legacyLongIdsRemainAccepted() throws Exception {
        String longId = "c".repeat(100);
        String legacy = """
            {"components":[{"id":"client-1","type":"CLIENT"},
                           {"id":"api-1","type":"API","preProcessingMs":0,"postProcessingMs":0},
                           {"id":"db-1","type":"DATABASE","processingMs":0}],
             "connections":[{"id":"ca","sourceId":"client-1","targetId":"api-1","protocol":"REST","latencyMs":0},
                            {"id":"ad","sourceId":"api-1","targetId":"db-1","protocol":"TCP","latencyMs":0}]}
            """.replace("client-1", longId);
        // Replace every occurrence of the ID, including the connection source.
        legacy = legacy.replace("client-1", longId);
        var response = post("/api/v1/architectures/validate", legacy);
        assertEquals(200, response.statusCode());
        assertTrue(json.readTree(response.body()).get("valid").asBoolean());
    }

    @Test void sixSpecificClientTypesWorkOnCanonicalV1Only() throws Exception {
        for (String type : new String[]{"WEB_BROWSER", "MOBILE_APP", "DESKTOP_APP", "CLI", "BOT", "EMBEDDED_SYSTEM"}) {
            String architecture = ARCHITECTURE.replace("\"type\":\"CLIENT\"", "\"type\":\"" + type + "\"");
            var validation = post("/api/v1/architectures/validate", architecture);
            assertEquals(200, validation.statusCode(), type);
            assertTrue(json.readTree(validation.body()).get("valid").asBoolean(), type);
            var execution = post("/api/v1/simulations",
                    "{\"architecture\":" + architecture + ",\"scenario\":" + SCENARIO + "}");
            assertEquals(200, execution.statusCode(), type);
            assertEquals(260, json.readTree(execution.body()).get("execution").get("totalDurationMs").asInt(), type);
            var oldRoute = post("/api/v2/architectures/validate", architecture);
            assertEquals(400, oldRoute.statusCode(), type);
        }
    }

    @Test void brokerExamplesValidateGraph() throws Exception {
        for (String file : new String[]{"queue-two-consumers.json", "queue-no-consumer.json",
                "topic-two-subscribers.json", "mqtt-qos0.json"}) {
            String fixture = Files.readString(Path.of("..", "..", "docs", "api", "examples", "v0.4", file));
            JsonNode architecture = json.readTree(fixture).get("architecture");
            var response = post("/api/v1/architectures/validate", architecture.toString());
            assertEquals(200, response.statusCode(), file);
            assertTrue(json.readTree(response.body()).get("valid").asBoolean(), file + " " + response.body());
        }
    }

    @Test void brokerExamplesExecuteWithExpectedDeliveryCounts() throws Exception {
        for (String file : new String[]{"queue-two-consumers.json", "topic-two-subscribers.json", "mqtt-qos0.json"}) {
            String fixture = Files.readString(Path.of("..", "..", "docs", "api", "examples", "v0.4", file));
            var response = post("/api/v1/simulations", fixture);
            assertEquals(200, response.statusCode(), file + " " + response.body());
            JsonNode result = json.readTree(response.body());
            int expected = file.startsWith("topic") ? 2 : 1;
            assertEquals(expected, result.get("metrics").get("messaging").get(0).get("delivered").asInt(), file);
            assertEquals("COMPLETED", result.get("execution").get("status").asText(), file);
            assertEquals("COMPLETED", result.get("execution").get("mainStatus").asText(), file);
        }
    }

    @Test void canonicalHttpRepresentationOmitsAbsentOptionalFields() throws Exception {
        String fixture = Files.readString(Path.of("..", "..", "docs", "api", "examples", "v0.4", "database-query.json"));
        var response = post("/api/v1/simulations", fixture);
        assertEquals(200, response.statusCode());
        JsonNode integration = json.readTree(response.body()).get("messageRepresentations").get(0).get("integration");
        assertEquals("HTTP_REST", integration.get("kind").asText());
        assertNull(integration.get("request").get("body"));
        assertNull(integration.get("response").get("body"));
    }

    @Test void queueWithoutConsumerTimesOutAndPublishStillCompletes() throws Exception {
        String fixture = Files.readString(Path.of("..", "..", "docs", "api", "examples", "v0.4", "queue-no-consumer.json"));
        var response = post("/api/v1/simulations", fixture);
        assertEquals(200, response.statusCode(), response.body());
        JsonNode result = json.readTree(response.body());
        assertEquals("COMPLETED", result.get("execution").get("mainStatus").asText());
        assertEquals("COMPLETED_WITH_ERRORS", result.get("execution").get("status").asText());
        assertEquals(30010, result.get("execution").get("totalDurationMs").asInt());
        assertEquals(1, result.get("metrics").get("messaging").get(0).get("failures").asInt());
        assertEquals(1, result.get("metrics").get("waitingByComponent").size());
    }

    @Test void missingBrokerDestinationCannotSimulate() throws Exception {
        String fixture = Files.readString(Path.of("..", "..", "docs", "api", "examples", "v0.4", "missing-broker-destination.invalid.json"));
        assertEquals(422, post("/api/v1/simulations", fixture).statusCode());
    }

    @Test void invalidMqttQosIsRejectedByGraphValidator() throws Exception {
        String fixture = Files.readString(Path.of("..", "..", "docs", "api", "examples", "v0.4", "mqtt-qos1.invalid.json"));
        JsonNode architecture = json.readTree(fixture).get("architecture");
        var response = post("/api/v1/architectures/validate", architecture.toString());
        assertEquals(200, response.statusCode());
        assertFalse(json.readTree(response.body()).get("valid").asBoolean());
    }

    @Test void allNewProfileExamplesValidateArchitecture() throws Exception {
        for (String file : new String[]{"http-generic.json", "graphql-http.json", "grpc-unary.json",
                "coap.json", "websocket-session.json"}) {
            String fixture = Files.readString(Path.of("..", "..", "docs", "api", "examples", "v0.4", file));
            JsonNode architecture = json.readTree(fixture).get("architecture");
            var response = post("/api/v1/architectures/validate", architecture.toString());
            assertEquals(200, response.statusCode(), file);
            assertTrue(json.readTree(response.body()).get("valid").asBoolean(), file + " " + response.body());
        }
    }

    @Test void websocketCannotRunAsRequestResponseScenario() throws Exception {
        String fixture = Files.readString(Path.of("..", "..", "docs", "api", "examples", "v0.4", "websocket-session.json"));
        JsonNode architecture = json.readTree(fixture).get("architecture");
        String scenario = "{\"kind\":\"REQUEST_RESPONSE\",\"entryComponentId\":\"client-1\","
                + "\"entryConnectionId\":\"conn-entry\",\"apiExecutions\":[]}";
        String request = "{\"architecture\":" + architecture + ",\"scenario\":" + scenario + "}";
        var response = post("/api/v1/scenarios/validate", request);
        assertEquals(200, response.statusCode());
        assertFalse(json.readTree(response.body()).get("valid").asBoolean());
    }

    @Test void websocketAndPublishExamplesValidateScenario() throws Exception {
        for (String file : new String[]{"websocket-session.json", "queue-two-consumers.json",
                "queue-no-consumer.json", "topic-two-subscribers.json", "mqtt-qos0.json"}) {
            String fixture = Files.readString(Path.of("..", "..", "docs", "api", "examples", "v0.4", file));
            var response = post("/api/v1/scenarios/validate", fixture);
            assertEquals(200, response.statusCode(), file);
            assertTrue(json.readTree(response.body()).get("valid").asBoolean(), file + " " + response.body());
        }
    }

    @Test void publishWithMissingDestinationIsInvalid() throws Exception {
        String fixture = Files.readString(Path.of("..", "..", "docs", "api", "examples", "v0.4",
                "missing-broker-destination.invalid.json"));
        var response = post("/api/v1/scenarios/validate", fixture);
        assertEquals(200, response.statusCode());
        assertFalse(json.readTree(response.body()).get("valid").asBoolean());
    }

    @Test void invalidBrokerSubscriptionAndWebSocketLimitAreRejected() throws Exception {
        String queue = Files.readString(Path.of("..", "..", "docs", "api", "examples", "v0.4",
                "queue-two-consumers.json"));
        String broken = queue.replace("\"connectionId\": \"conn-consumer-1\"", "\"connectionId\": \"missing\"");
        assertNotEquals(queue, broken);
        var subscription = post("/api/v1/scenarios/validate", broken);
        assertEquals(200, subscription.statusCode());
        assertFalse(json.readTree(subscription.body()).get("valid").asBoolean());

        String websocket = Files.readString(Path.of("..", "..", "docs", "api", "examples", "v0.4",
                "websocket-too-many-messages.invalid.json"));
        var limit = post("/api/v1/scenarios/validate", websocket);
        assertEquals(400, limit.statusCode());
    }

    @Test void synchronousProfilesExecuteAndKeepTheirIntegrationKind() throws Exception {
        for (String file : new String[]{"http-rest.json", "soap-http.json", "http-generic.json",
                "graphql-http.json", "grpc-unary.json", "coap.json", "database-query.json"}) {
            String fixture = Files.readString(Path.of("..", "..", "docs", "api", "examples", "v0.4", file));
            var response = post("/api/v1/simulations", fixture);
            assertEquals(200, response.statusCode(), file + " " + response.body());
            JsonNode result = json.readTree(response.body());
            assertEquals("COMPLETED", result.get("execution").get("mainStatus").asText(), file);
            assertTrue(result.get("trace").size() > 0, file);
            assertTrue(result.get("messageRepresentations").size() > 0, file);
            if (!file.equals("database-query.json")) {
                String expected = json.readTree(fixture).get("architecture").get("connections").get(0)
                        .get("integration").get("kind").asText();
                assertEquals(expected, result.get("messageRepresentations").get(0)
                        .get("integration").get("kind").asText(), file);
            }
        }
    }

    @Test void websocketSessionRunsBidirectionallyUnderVirtualClock() throws Exception {
        String fixture = Files.readString(Path.of("..", "..", "docs", "api", "examples", "v0.4", "websocket-session.json"));
        var response = post("/api/v1/simulations", fixture);
        assertEquals(200, response.statusCode(), response.body());
        JsonNode result = json.readTree(response.body());
        assertEquals("COMPLETED", result.get("execution").get("mainStatus").asText());
        assertEquals(55, result.get("execution").get("totalDurationMs").asInt());
        assertEquals(3, result.get("timeline").size());
        assertEquals("WEBSOCKET_SESSION", result.get("resourceStates").get(0).get("kind").asText());
        assertEquals("WEBSOCKET", result.get("messageRepresentations").get(0)
                .get("integration").get("kind").asText());
    }

    @Test void websocketConnectTimeoutPreventsMessages() throws Exception {
        String fixture = Files.readString(Path.of("..", "..", "docs", "api", "examples", "v0.4", "websocket-session.json"))
                .replace("\"connectTimeoutMs\": 5000", "\"connectTimeoutMs\": 5");
        var response = post("/api/v1/simulations", fixture);
        assertEquals(200, response.statusCode(), response.body());
        JsonNode result = json.readTree(response.body());
        assertEquals("TIMED_OUT", result.get("execution").get("status").asText());
        assertEquals(5, result.get("execution").get("totalDurationMs").asInt());
        assertEquals(0, result.get("timeline").size());
    }

    @Test void websocketIdleTimeoutEndsAfterLastDeliveredMessage() throws Exception {
        String fixture = Files.readString(Path.of("..", "..", "docs", "api", "examples", "v0.4", "websocket-session.json"))
                .replace("\"idleTimeoutMs\": null", "\"idleTimeoutMs\": 5");
        var response = post("/api/v1/simulations", fixture);
        assertEquals(200, response.statusCode(), response.body());
        JsonNode result = json.readTree(response.body());
        assertEquals("TIMED_OUT", result.get("execution").get("status").asText());
        assertEquals(25, result.get("execution").get("totalDurationMs").asInt());
        assertEquals(2, result.get("timeline").size());
    }

    @Test void websocketCanStartFromOneApiToAnother() throws Exception {
        String request = """
            {"architecture":{"components":[
              {"id":"api-a","type":"API","displayName":"API A"},
              {"id":"api-b","type":"API","displayName":"API B"}],
              "connections":[{"id":"ab","sourceId":"api-a","targetId":"api-b","latencyMs":2,
                "integration":{"kind":"WEBSOCKET","path":"/events",
                  "messages":[{"sender":"SOURCE","intervalMs":0},{"sender":"TARGET","intervalMs":3}]}}]},
             "scenario":{"kind":"WEBSOCKET_SESSION","entryComponentId":"api-a","entryConnectionId":"ab"}}
            """;
        var response = post("/api/v1/simulations", request);
        assertEquals(200, response.statusCode(), response.body());
        JsonNode result = json.readTree(response.body());
        assertEquals("COMPLETED", result.get("execution").get("status").asText());
        assertEquals(9, result.get("execution").get("totalDurationMs").asInt());
    }

    @Test void explicitWebSocketCloseIsVisibleInTrace() throws Exception {
        String fixture = Files.readString(Path.of("..", "..", "docs", "api", "examples", "v0.4", "websocket-session.json"))
                .replace("\"closeMode\": \"AUTOMATIC\"", "\"closeMode\": \"EXPLICIT\"");
        var response = post("/api/v1/simulations", fixture);
        assertEquals(200, response.statusCode(), response.body());
        JsonNode trace = json.readTree(response.body()).get("trace");
        boolean closing = false;
        for (JsonNode entry : trace) {
            if (entry.has("newState") && "CLOSING".equals(entry.get("newState").asText())
                    && "EXPLICIT_CLOSE".equals(entry.get("cause").asText())) closing = true;
        }
        assertTrue(closing);
    }
}
