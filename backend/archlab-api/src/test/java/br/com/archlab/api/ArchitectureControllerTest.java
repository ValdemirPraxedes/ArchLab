package br.com.archlab.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.server.test.LocalServerPort;
import org.springframework.http.*;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ArchitectureControllerTest {
    @Autowired TestRestTemplate http;
    @LocalServerPort int port;
    private String url(String path) { return "http://localhost:" + port + "/api/v1/" + path; }
    private static final String VALID = """
            {"components":[{"id":"client-1","type":"CLIENT"},
             {"id":"api-1","type":"API","preProcessingMs":30,"postProcessingMs":20},
             {"id":"db-1","type":"DATABASE","processingMs":150}],
             "connections":[{"id":"connection-1","sourceId":"client-1","targetId":"api-1","protocol":"REST","latencyMs":20},
             {"id":"connection-2","sourceId":"api-1","targetId":"db-1","protocol":"TCP","latencyMs":10}]}
            """;
    private ResponseEntity<Map> post(String path, String json) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url(path), HttpMethod.POST, new HttpEntity<>(json, headers), Map.class);
    }
    @Test void validArchitectureReturns200() {
        ResponseEntity<Map> response = post("architectures/validate", VALID);
        assertEquals(200, response.getStatusCode().value());
        assertEquals(true, response.getBody().get("valid"));
        assertEquals(0, ((java.util.List<?>)response.getBody().get("violations")).size());
    }
    @Test void missingDatabaseReturns200ButSimulation422() {
        String invalid = """
                {"components":[{"id":"client-1","type":"CLIENT"},
                {"id":"api-1","type":"API","preProcessingMs":30,"postProcessingMs":20}],
                "connections":[]}
                """;
        assertEquals(false, post("architectures/validate", invalid).getBody().get("valid"));
        ResponseEntity<Map> response = post("simulations", invalid);
        assertEquals(422, response.getStatusCode().value());
        assertEquals("ARCHITECTURE_INVALID", response.getBody().get("code"));
        assertFalse(((java.util.List<?>)response.getBody().get("violations")).isEmpty());
    }
    @Test void simulationHasFullResult260ms() {
        ResponseEntity<Map> response = post("simulations", VALID);
        assertEquals(200, response.getStatusCode().value());
        Map body = response.getBody();
        assertEquals(260, ((Number)((Map)body.get("execution")).get("totalDurationMs")).intValue());
        assertEquals(7, ((java.util.List<?>)body.get("timeline")).size());
        assertEquals(170, ((Number)((Map)body.get("metrics")).get("apiWaitingMs")).intValue());
        assertTrue(((java.util.List<?>)body.get("trace")).size() >= 20);
    }
    @Test void malformedJsonReturns400() {
        ResponseEntity<Map> response = post("simulations", "{bad");
        assertEquals(400, response.getStatusCode().value());
        assertEquals("INVALID_REQUEST", response.getBody().get("code"));
    }
    @Test void wrongDiscriminatorOrMissingRequiredFieldReturns400() {
        String invalid = """
                {"components":[{"id":"c","type":"CACHE"}],"connections":[]}
                """;
        assertEquals(400, post("architectures/validate", invalid).getStatusCode().value());
        assertEquals(400, post("simulations", "{\"components\":[],\"connections\":[{\"id\":\"c\"}]}").getStatusCode().value());
    }
}
