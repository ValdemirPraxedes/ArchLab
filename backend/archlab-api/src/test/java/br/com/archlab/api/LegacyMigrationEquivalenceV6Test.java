package br.com.archlab.api;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/**
 * V6-14 / decision 2: a legacy architecture and its explicit migrate-v6 result produce the same
 * execution (status, total virtual duration, response) and the same timeline timing, while keeping
 * component, connection and Action IDs and dependencies. Fixtures: docs/api/examples/v0.6/migration.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LegacyMigrationEquivalenceV6Test {
    private static final Path FIXTURES=Path.of("..","..","docs","api","examples","v0.6","migration");
    @Value("${local.server.port}") int port;
    private final HttpClient client=HttpClient.newHttpClient();
    private final JsonMapper json=new JsonMapper();

    @TestFactory Stream<DynamicTest> migratedArchitecturesSimulateEquivalently() throws Exception {
        List<Path> files;
        try (Stream<Path> listing=Files.list(FIXTURES)) {
            files=listing.filter(p -> p.getFileName().toString().matches("M6-\\d\\d\\.json")).sorted().toList();
        }
        assertEquals(4,files.size(),files.toString());
        return files.stream().map(file -> DynamicTest.dynamicTest(file.getFileName().toString(),() -> check(file)));
    }

    private void check(Path file) throws Exception {
        JsonNode fixture=json.readTree(Files.readString(file));
        String id=fixture.get("id").asText();
        JsonNode legacy=fixture.get("legacy");
        JsonNode architecture=legacy.get("architecture");

        var migration=post("/api/v1/architectures/migrate-v6",architecture.toString());
        assertEquals(200,migration.statusCode(),id+": "+migration.body());
        JsonNode result=json.readTree(migration.body());
        JsonNode document=result.get("document");
        JsonNode report=result.get("report");

        // Expected conversion report (V6-03)
        List<String> ids=new ArrayList<>();
        report.get("databaseIds").forEach(n -> ids.add(n.asText()));
        List<String> expectedIds=new ArrayList<>();
        fixture.get("expectedReport").get("databaseIds").forEach(n -> expectedIds.add(n.asText()));
        assertEquals(expectedIds,ids,id);
        String notes=report.get("notes").toString();
        for (JsonNode note:fixture.get("expectedReport").get("notesContain"))
            assertTrue(notes.contains(note.asText()),id+" note '"+note.asText()+"' in "+notes);

        // Nothing historical left; IDs, connections and dependencies preserved
        assertEquals("0.6",document.get("schemaVersion").asText(),id);
        assertFalse(document.toString().contains("\"DATABASE\""),id);
        assertFalse(document.toString().contains("\"CALL\"") && callsTargetMigratedDatabase(document,ids),id);
        assertEquals(idsOf(architecture.get("components")),idsOf(document.get("components")),id);
        assertEquals(json.readTree(stripTypes(architecture.get("connections"))),json.readTree(stripTypes(document.get("connections"))),id);
        assertEquals(actionShape(architecture),actionShape(document),id);

        // Same execution before and after
        String before=simulation(architecture,legacy.get("scenario"));
        String after=simulation(document,legacy.get("scenario"));
        var original=post("/api/v1/simulations",before);
        var migrated=post("/api/v1/simulations",after);
        assertEquals(200,original.statusCode(),id+" original: "+original.body());
        assertEquals(200,migrated.statusCode(),id+" migrated: "+migrated.body());
        JsonNode o=json.readTree(original.body()), m=json.readTree(migrated.body());
        for (String field:List.of("status","mainStatus","totalDurationMs","responseStatusCode"))
            assertEquals(o.get("execution").get(field),m.get("execution").get(field),id+" execution."+field);
        assertEquals(timing(o),timing(m),id+" timeline timing");
        assertTrue(m.get("dataOutcomes").size()>0,id+" migrated run must expose dataOutcomes");
        assertEquals(migrated.body(),post("/api/v1/simulations",after).body(),id+" replay");
    }

    private boolean callsTargetMigratedDatabase(JsonNode document,List<String> databaseIds) {
        Set<String> edges=new HashSet<>();
        for (JsonNode edge:document.get("connections"))
            if (databaseIds.contains(edge.get("targetId").asText())) edges.add(edge.get("id").asText());
        for (JsonNode component:document.get("components"))
            for (JsonNode behavior:component.path("behaviors"))
                for (JsonNode action:behavior.path("actions"))
                    if ("CALL".equals(action.path("kind").asText()) && edges.contains(action.path("connectionId").asText()))
                        return true;
        return false;
    }
    private static List<String> idsOf(JsonNode components) {
        List<String> ids=new ArrayList<>();
        components.forEach(c -> ids.add(c.get("id").asText()));
        return ids;
    }
    private static String stripTypes(JsonNode connections) { return connections.toString(); }
    /** Behavior and Action IDs, connections and dependencies, ignoring kind changes (CALL -> DB_SELECT). */
    private static List<String> actionShape(JsonNode architecture) {
        List<String> shape=new ArrayList<>();
        for (JsonNode component:architecture.get("components"))
            for (JsonNode behavior:component.path("behaviors"))
                for (JsonNode action:behavior.path("actions"))
                    shape.add(component.get("id").asText()+"/"+behavior.get("id").asText()+"/"+action.get("id").asText()
                            +" conn="+action.path("connectionId").asText()+" deps="+action.path("dependsOn"));
        return shape;
    }
    private static List<String> timing(JsonNode response) {
        List<String> rows=new ArrayList<>();
        for (JsonNode activity:response.get("timeline"))
            rows.add(activity.path("kind").asText()+"|"+activity.path("componentId").asText()+"|"
                    +activity.path("connectionId").asText()+"|"+activity.path("startMs").asLong()+"|"+activity.path("endMs").asLong());
        return rows;
    }
    private String simulation(JsonNode architecture,JsonNode scenario) {
        ObjectNode request=json.createObjectNode();
        request.set("architecture",architecture);
        request.set("scenario",scenario);
        return request.toString();
    }
    private HttpResponse<String> post(String path,String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path))
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
}
