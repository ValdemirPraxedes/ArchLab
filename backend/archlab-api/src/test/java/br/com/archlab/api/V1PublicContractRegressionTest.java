package br.com.archlab.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Public v1 wire-contract regression suite. Keep this suite unchanged while introducing v2.
 * In particular: v1 requires a Database and preserves legacy REST/SOAP/TCP labels.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class V1PublicContractRegressionTest {
    @Value("${local.server.port}") int port;
    @Autowired JsonMapper json;
    private final HttpClient client = HttpClient.newHttpClient();

    private static final String REFERENCE = """
        {
          "components": [
            {"id":"client-1","type":"CLIENT"},
            {"id":"api-1","type":"API","preProcessingMs":30,"postProcessingMs":20},
            {"id":"db-1","type":"DATABASE","processingMs":150}
          ],
          "connections": [
            {"id":"connection-1","sourceId":"client-1","targetId":"api-1","protocol":"REST","latencyMs":20},
            {"id":"connection-2","sourceId":"api-1","targetId":"db-1","protocol":"TCP","latencyMs":10}
          ]
        }
        """;

    private HttpResponse<String> post(String endpoint, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/api/v1/" + endpoint))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test void referenceArchitectureKeepsV1ValidationShape() throws Exception {
        var response = post("architectures/validate", REFERENCE);
        assertEquals(200, response.statusCode());
        JsonNode root = json.readTree(response.body());
        assertTrue(root.get("valid").asBoolean());
        assertEquals(0, root.get("violations").size());
    }

    @Test void referenceSimulationKeeps260msSevenActivitiesAnd170msWait() throws Exception {
        var response = post("simulations", REFERENCE);
        assertEquals(200, response.statusCode());
        JsonNode root = json.readTree(response.body());
        assertEquals("COMPLETED", root.get("execution").get("status").asText());
        assertEquals(260L, root.get("execution").get("totalDurationMs").asLong());
        JsonNode timeline = root.get("timeline");
        assertEquals(7, timeline.size());
        long[] expectedStart = {0, 20, 50, 60, 210, 220, 240};
        long[] expectedDuration = {20, 30, 10, 150, 10, 20, 20};
        for (int i = 0; i < 7; i++) {
            JsonNode activity = timeline.get(i);
            assertEquals(expectedStart[i], activity.get("startMs").asLong(), "start " + i);
            assertEquals(expectedDuration[i], activity.get("durationMs").asLong(), "duration " + i);
            assertEquals(expectedStart[i] + expectedDuration[i],
                    activity.get("endMs").asLong(), "end " + i);
        }
        JsonNode metrics = root.get("metrics");
        assertEquals(30L, metrics.get("apiPreProcessingMs").asLong());
        assertEquals(20L, metrics.get("apiPostProcessingMs").asLong());
        assertEquals(150L, metrics.get("databaseProcessingMs").asLong());
        assertEquals(60L, metrics.get("communicationMs").asLong());
        assertEquals(170L, metrics.get("apiWaitingMs").asLong());
        assertEquals(260L, metrics.get("apiPreProcessingMs").asLong()
                + metrics.get("apiPostProcessingMs").asLong()
                + metrics.get("databaseProcessingMs").asLong()
                + metrics.get("communicationMs").asLong());
        assertEquals("CREATED", root.get("initialState").get("requests").get("request-1").asText());
    }

    @Test void v1TraceKeepsSequentialTimestampedCorrelatedEvents() throws Exception {
        JsonNode trace = json.readTree(post("simulations", REFERENCE).body()).get("trace");
        assertTrue(trace.size() >= 20);
        long lastTime = -1;
        Set<String> kinds = new HashSet<>();
        for (int i = 0; i < trace.size(); i++) {
            JsonNode entry = trace.get(i);
            assertEquals(i + 1L, entry.get("sequence").asLong());
            long time = entry.get("timestampMs").asLong();
            assertTrue(time >= lastTime, "trace order at index " + i);
            lastTime = time;
            kinds.add(entry.get("type").asText());
            if ("MESSAGE_EVENT".equals(entry.get("type").asText())) {
                assertEquals("request-1", entry.get("requestId").asText());
                assertFalse(entry.get("exchangeId").asText().isBlank());
                assertFalse(entry.get("messageId").asText().isBlank());
            }
        }
        assertTrue(kinds.contains("STATE_TRANSITION"));
        assertTrue(kinds.contains("MESSAGE_EVENT"));
        assertTrue(kinds.contains("PROCESSING_EVENT"));
        assertEquals(260L, lastTime);
    }

    @Test void v1AcceptsSoapLabelButRequiresDatabase() throws Exception {
        String soap = REFERENCE.replace("\"protocol\":\"REST\"", "\"protocol\":\"SOAP\"");
        assertEquals(200, post("simulations", soap).statusCode());
        String noDb = """
            {"components":[{"id":"c","type":"CLIENT"},
            {"id":"a","type":"API","preProcessingMs":1,"postProcessingMs":1}],
            "connections":[{"id":"ca","sourceId":"c","targetId":"a",
            "protocol":"REST","latencyMs":1}]}
            """;
        JsonNode validation = json.readTree(post("architectures/validate", noDb).body());
        assertFalse(validation.get("valid").asBoolean());
        var invalidRun = post("simulations", noDb);
        assertEquals(422, invalidRun.statusCode());
        assertEquals("ARCHITECTURE_INVALID",
                json.readTree(invalidRun.body()).get("code").asText());
    }

    @Test void invalidRequestUsesProblemDetails() throws Exception {
        var response = post("simulations", "{broken");
        assertEquals(400, response.statusCode());
        assertTrue(response.headers().firstValue("content-type").orElse("")
                .contains("application/problem+json"));
        JsonNode problem = json.readTree(response.body());
        assertEquals(400, problem.get("status").asInt());
        assertEquals("INVALID_REQUEST", problem.get("code").asText());
        assertEquals("/api/v1/simulations", problem.get("instance").asText());
    }
}
