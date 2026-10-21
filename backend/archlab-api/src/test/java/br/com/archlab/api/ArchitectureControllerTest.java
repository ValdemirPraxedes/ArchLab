package br.com.archlab.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArchitectureControllerTest {
    @Value("${local.server.port}") int port;
    @Autowired JsonMapper json;
    private final HttpClient client = HttpClient.newHttpClient();
    private String url(String path) { return "http://localhost:" + port + "/api/v1/" + path; }
    private static final String VALID = """
            {"components":[{"id":"client-1","type":"CLIENT"},
             {"id":"api-1","type":"API","preProcessingMs":30,"postProcessingMs":20},
             {"id":"db-1","type":"DATABASE","processingMs":150}],
             "connections":[{"id":"connection-1","sourceId":"client-1","targetId":"api-1","protocol":"REST","latencyMs":20},
             {"id":"connection-2","sourceId":"api-1","targetId":"db-1","protocol":"TCP","latencyMs":10}]}
            """;
    private HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url(path)))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
    @Test void validArchitectureReturns200() throws Exception {
        HttpResponse<String> response = post("architectures/validate", VALID);
        assertEquals(200, response.statusCode());
        JsonNode data = json.readTree(response.body());
        assertTrue(data.get("valid").asBoolean());
        assertEquals(0, data.get("violations").size());
    }
    @Test void missingDatabaseReturns200ButSimulation422() throws Exception {
        String invalid = """
                {"components":[{"id":"client-1","type":"CLIENT"},
                {"id":"api-1","type":"API","preProcessingMs":30,"postProcessingMs":20}],
                "connections":[]}
                """;
        HttpResponse<String> validation = post("architectures/validate", invalid);
        assertEquals(200, validation.statusCode());
        assertFalse(json.readTree(validation.body()).get("valid").asBoolean());
        HttpResponse<String> simulation = post("simulations", invalid);
        assertEquals(422, simulation.statusCode());
        JsonNode data = json.readTree(simulation.body());
        assertEquals("ARCHITECTURE_INVALID", data.get("code").asText());
        assertTrue(data.get("violations").size() > 0);
    }
    @Test void simulationHasFullResult260ms() throws Exception {
        HttpResponse<String> response = post("simulations", VALID);
        assertEquals(200, response.statusCode());
        JsonNode data = json.readTree(response.body());
        assertEquals(260, data.get("execution").get("totalDurationMs").asInt());
        assertEquals(7, data.get("timeline").size());
        assertEquals(170, data.get("metrics").get("apiWaitingMs").asInt());
        assertTrue(data.get("trace").size() >= 20);
    }
    @Test void malformedJsonReturns400() throws Exception {
        HttpResponse<String> response = post("simulations", "{bad");
        assertEquals(400, response.statusCode());
        assertEquals("INVALID_REQUEST", json.readTree(response.body()).get("code").asText());
    }
    @Test void wrongDiscriminatorOrMissingRequiredFieldReturns400() throws Exception {
        assertEquals(400, post("architectures/validate",
                "{\"components\":[{\"id\":\"c\",\"type\":\"CACHE\"}],\"connections\":[]}").statusCode());
        assertEquals(400, post("simulations",
                "{\"components\":[],\"connections\":[{\"id\":\"c\"}]}").statusCode());
    }
}
