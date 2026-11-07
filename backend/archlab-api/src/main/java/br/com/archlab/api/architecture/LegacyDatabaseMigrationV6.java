package br.com.archlab.api.architecture;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.util.*;

/** Explicit copy-based conversion of unambiguous historical DATABASE nodes. */
public final class LegacyDatabaseMigrationV6 {
    private LegacyDatabaseMigrationV6() {}

    public record Report(List<String> componentIds,List<String> notes) {
        public Report { componentIds=List.copyOf(componentIds); notes=List.copyOf(notes); }
    }
    public record Result(JsonNode document,Report report) {}

    public static Result migrate(JsonNode input) {
        if (input==null || !input.isObject()) throw new IllegalArgumentException("Expected document object");
        JsonNode original=input.has("architecture")?input.get("architecture"):input;
        if (original==null || !original.isObject() || !original.path("components").isArray()
                || !original.path("connections").isArray())
            throw new IllegalArgumentException("Expected architecture components and connections arrays");
        JsonNode copy=input.deepCopy();
        ObjectNode architecture=(ObjectNode)(copy.has("architecture")?copy.get("architecture"):copy);
        JsonNode components=architecture.get("components");
        JsonNode connections=architecture.get("connections");
        List<String> ids=new ArrayList<>(), notes=new ArrayList<>();
        for (JsonNode component:components) {
            if (!"DATABASE".equals(component.path("type").asText())) continue;
            if (!(component instanceof ObjectNode database))
                throw new IllegalArgumentException("Invalid DATABASE component");
            String id=database.path("id").asText();
            if (id.isBlank()) throw new IllegalArgumentException("DATABASE requires id");
            for (JsonNode edge:connections) {
                if (!id.equals(edge.path("targetId").asText())) continue;
                JsonNode integration=edge.path("integration");
                if (!"DATABASE_QUERY".equals(integration.path("kind").asText())
                        || !integration.path("queryText").asText().isBlank()
                        || !integration.path("responseText").asText().isBlank())
                    throw new IllegalArgumentException("DATABASE "+id+": connection requires semantic review");
                String edgeId=edge.path("id").asText();
                for (JsonNode actor:components) for (JsonNode behavior:actor.path("behaviors"))
                    for (JsonNode action:behavior.path("actions"))
                        if ("CALL".equals(action.path("kind").asText())
                                && edgeId.equals(action.path("connectionId").asText()))
                            throw new IllegalArgumentException("DATABASE "+id+": historical CALL requires semantic review");
            }
            long processing=database.path("processingMs").asLong(-1);
            if (processing<0 || processing>1_000_000_000L)
                throw new IllegalArgumentException("DATABASE "+id+": invalid processingMs");
            database.remove("processingMs");
            database.put("type","RELATIONAL_DATABASE");
            ObjectNode properties=database.putObject("dataProperties");
            properties.put("initialRecordCount",0);
            properties.put("averageRecordBytes",512);
            properties.put("readLatencyMs",processing);
            properties.put("writeLatencyMs",processing);
            properties.put("maxConcurrentOperations",1);
            properties.put("connectionPoolSize",1);
            properties.put("queueCapacity",10);
            ids.add(id);
            notes.add(id+": processingMs "+processing+" mapped to read/write latency; empty initial records; 512 bytes/record");
        }
        architecture.put("schemaVersion","0.6");
        return new Result(copy,new Report(ids,notes));
    }
}
