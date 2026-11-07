package br.com.archlab.api;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/**
 * V6-04 / decision 1: the backend accepts and rejects exactly the Architecture documents that the
 * OpenAPI accepts and rejects (docs/api/examples/v0.6/contract-cases.json, also checked by validate.cjs).
 * Rejection may be structural (HTTP 400) or semantic (HTTP 200 with valid=false).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ContractCasesV6Test {
    private static final Path CASES=Path.of("..","..","docs","api","examples","v0.6","contract-cases.json");
    @Value("${local.server.port}") int port;
    private final HttpClient client=HttpClient.newHttpClient();
    private final JsonMapper json=new JsonMapper();

    @TestFactory Stream<DynamicTest> backendMatchesContractCases() throws Exception {
        List<DynamicTest> tests=new ArrayList<>();
        for (JsonNode c:json.readTree(Files.readString(CASES)).get("cases"))
            tests.add(DynamicTest.dynamicTest(c.get("name").asText(),() -> {
                var response=client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v1/architectures/validate"))
                        .header("Content-Type","application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(c.get("architecture").toString())).build(),
                        HttpResponse.BodyHandlers.ofString());
                boolean accepted=response.statusCode()==200 && json.readTree(response.body()).path("valid").asBoolean(false);
                assertEquals(c.get("valid").asBoolean(),accepted,c.get("name").asText()+": "+response.statusCode()+" "+response.body());
                if (!accepted) assertTrue(response.statusCode()==400 || response.statusCode()==200,response.body());
            }));
        assertTrue(tests.size()>=14);
        return tests.stream();
    }
}
