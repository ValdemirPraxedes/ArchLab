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

    /**
     * A historical DATABASE whose semantics cannot be converted without a human decision.
     * Carries a stable diagnostic (DATA_MIGRATION_AMBIGUOUS) instead of a generic parse error.
     */
    public static final class AmbiguousMigrationException extends IllegalArgumentException {
        private final String componentId, path, reason;
        public AmbiguousMigrationException(String componentId,String path,String reason,String message) {
            super(message);
            this.componentId=componentId; this.path=path; this.reason=reason;
        }
        public String componentId() {return componentId;}
        public String path() {return path;}
        public String reason() {return reason;}
    }

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
        int componentIndex=-1;
        for (JsonNode component:components) {
            componentIndex++;
            if (!"DATABASE".equals(component.path("type").asText())) continue;
            if (!(component instanceof ObjectNode database))
                throw new IllegalArgumentException("Invalid DATABASE component");
            String id=database.path("id").asText();
            if (id.isBlank()) throw new IllegalArgumentException("DATABASE requires id");
            int edgeIndex=-1;
            for (JsonNode edge:connections) {
                edgeIndex++;
                if (!id.equals(edge.path("targetId").asText())) continue;
                JsonNode integration=edge.path("integration");
                if (!"DATABASE_QUERY".equals(integration.path("kind").asText())
                        || !integration.path("queryText").asText().isBlank()
                        || !integration.path("responseText").asText().isBlank())
                    throw new AmbiguousMigrationException(id,"connections["+edgeIndex+"].integration",
                            !"DATABASE_QUERY".equals(integration.path("kind").asText())?"NON_QUERY_CONNECTION"
                                    :"HISTORICAL_QUERY_TEXT",
                            "DATABASE "+id+": connection "+edge.path("id").asText()
                                    +" carries historical query semantics that require an explicit choice");
                String edgeId=edge.path("id").asText();
                int actorIndex=-1;
                for (JsonNode actor:components) {
                    actorIndex++;
                    int behaviorIndex=-1;
                    for (JsonNode behavior:actor.path("behaviors")) {
                        behaviorIndex++;
                        int actionIndex=-1;
                        for (JsonNode action:behavior.path("actions")) {
                            actionIndex++;
                            if ("CALL".equals(action.path("kind").asText())
                                    && edgeId.equals(action.path("connectionId").asText()))
                                throw new AmbiguousMigrationException(id,"components["+actorIndex+"].behaviors["
                                        +behaviorIndex+"].actions["+actionIndex+"]","HISTORICAL_CALL",
                                        "DATABASE "+id+": historical CALL "+action.path("id").asText()
                                                +" has no unambiguous data Action equivalent");
                        }
                    }
                }
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
