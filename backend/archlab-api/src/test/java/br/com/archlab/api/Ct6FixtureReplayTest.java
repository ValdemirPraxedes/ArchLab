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
 * V6-21: replays the CT-6-01..10 acceptance fixtures stored in docs/api/examples/v0.6/ct.
 * Each case is posted to /api/v1, checked against its declared expectations and, when
 * {@code replay} is set, re-executed to prove byte-identical responses (determinism and
 * clean per-execution virtual state).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class Ct6FixtureReplayTest {
    private static final Path FIXTURES=Path.of("..","..","docs","api","examples","v0.6","ct");
    @Value("${local.server.port}") int port;
    private final HttpClient client=HttpClient.newHttpClient();
    private final JsonMapper json=new JsonMapper();

    @TestFactory Stream<DynamicTest> ctFixturesReplayAsDocumented() throws Exception {
        List<Path> files;
        try (Stream<Path> listing=Files.list(FIXTURES)) {
            files=listing.filter(p -> p.getFileName().toString().matches("CT-6-\\d\\d\\.json")).sorted().toList();
        }
        assertEquals(10,files.size(),"CT-6-01..10 fixtures must all exist: "+files);
        List<DynamicTest> tests=new ArrayList<>();
        for (Path file:files) {
            JsonNode fixture=json.readTree(Files.readString(file));
            for (JsonNode testCase:fixture.get("cases"))
                tests.add(DynamicTest.dynamicTest(fixture.get("id").asText()+" "+testCase.get("name").asText(),
                        () -> run(fixture.get("id").asText(),testCase)));
        }
        return tests.stream();
    }

    private void run(String id,JsonNode testCase) throws Exception {
        String endpoint=testCase.get("endpoint").asText();
        assertTrue(endpoint.startsWith("/api/v1/"),id+" must use /api/v1");
        String request=testCase.get("request").toString();
        HttpResponse<String> response=post(endpoint,request);
        JsonNode expect=testCase.get("expect");
        String context=id+"/"+testCase.get("name").asText()+": "+response.body();
        assertEquals(expect.get("status").asInt(),response.statusCode(),context);
        JsonNode body=json.readTree(response.body());
        for (JsonNode text:expect.path("bodyContains"))
            assertTrue(response.body().contains(text.asText()),"missing "+text+" in "+context);
        for (var field:expect.path("fields").properties())
            assertEquals(field.getValue(),body.at(field.getKey()),field.getKey()+" in "+context);
        for (JsonNode text:expect.path("documentNotContains"))
            assertFalse(body.get("document").toString().contains(text.asText()),"unexpected "+text+" in "+context);
        if (expect.has("dataOutcomes")) {
            JsonNode outcomes=body.get("dataOutcomes");
            assertNotNull(outcomes,context);
            int previous=-1;
            for (JsonNode expected:expect.get("dataOutcomes")) {
                String actionId=expected.get("actionId").asText();
                int index=-1;
                for (int i=0;i<outcomes.size();i++)
                    if (actionId.equals(outcomes.get(i).path("actionId").asText())) {index=i;break;}
                assertTrue(index>=0,"no outcome for "+actionId+" in "+context);
                assertTrue(index>previous,"outcome order for "+actionId+" in "+context);
                previous=index;
                JsonNode actual=outcomes.get(index);
                for (var property:expected.properties()) {
                    JsonNode want=property.getValue(), got=actual.get(property.getKey());
                    assertNotNull(got,property.getKey()+" of "+actionId+" in "+context);
                    if (want.isObject() && want.has("$gt"))
                        assertTrue(got.asLong()>want.get("$gt").asLong(),property.getKey()+" of "+actionId+" in "+context);
                    else assertEquals(want,got,property.getKey()+" of "+actionId+" in "+context);
                }
            }
        }
        if (testCase.path("replay").asBoolean(false)) {
            int count=testCase.path("replayCount").asInt(1);
            for (int i=0;i<count;i++)
                assertEquals(response.body(),post(endpoint,request).body(),id+" replay "+(i+1)+" diverged");
        }
    }

    private HttpResponse<String> post(String path,String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path))
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
