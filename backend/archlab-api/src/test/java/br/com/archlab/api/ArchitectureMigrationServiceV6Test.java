package br.com.archlab.api;

import br.com.archlab.api.v2.V2RequestMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectureMigrationServiceV6Test {
    private final JsonMapper json=new JsonMapper();
    private final ArchitectureMigrationServiceV6 migration=new ArchitectureMigrationServiceV6(new V2RequestMapper());
    private static final String BASE="""
        {"components":[
          {"id":"client","type":"CLIENT","displayName":"Client"},
          {"id":"service","type":"SERVICE","displayName":"Service"},
          {"id":"db","type":"DATABASE","displayName":"DB","processingMs":3}
        ],"connections":[
          {"id":"entry","sourceId":"client","targetId":"service","integration":{"kind":"HTTP_REST"}},
          {"id":"data","sourceId":"service","targetId":"db","integration":{"kind":"DATABASE_QUERY"}}
        ]}
        """;

    @Test void convertsCopyAndPreservesIdsConnectionsAndLatency() {
        var input=json.readTree(BASE);
        String before=input.toString();
        var result=migration.migrate(input);
        assertEquals(before,input.toString());
        assertEquals("0.6",result.document().get("schemaVersion").asText());
        assertEquals("RELATIONAL_DATABASE",result.document().get("components").get(2).get("type").asText());
        assertEquals(3,result.document().get("components").get(2).get("dataProperties")
                .get("readLatencyMs").asInt());
        assertEquals("data",result.document().get("connections").get(1).get("id").asText());
        assertEquals("DATABASE_QUERY",result.document().get("connections").get(1)
                .get("integration").get("kind").asText());
        assertEquals("db",result.report().databaseIds().getFirst());
    }
    private static final String WITH_CALL=BASE.replace("\"displayName\":\"Service\"",
            "\"displayName\":\"Service\",\"behaviors\":[{\"id\":\"b\",\"trigger\":\"REQUEST_RECEIVED\",\"actions\":["
            +"{\"id\":\"call\",\"kind\":\"CALL\",\"connectionId\":\"data\"},"
            +"{\"id\":\"respond\",\"kind\":\"RESPOND\",\"dependsOn\":[\"call\"]}]}]");

    @Test void keepsHistoricalQueryTextAsMessageRepresentation() {
        var input=json.readTree(BASE.replace("\"kind\":\"DATABASE_QUERY\"",
                "\"kind\":\"DATABASE_QUERY\",\"queryText\":\"SELECT * FROM t\",\"responseText\":\"1 row\""));
        String before=input.toString();
        var result=migration.migrate(input);
        assertEquals(before,input.toString());
        var integration=result.document().get("connections").get(1).get("integration");
        assertEquals("SELECT * FROM t",integration.get("queryText").asText());
        assertEquals("1 row",integration.get("responseText").asText());
        assertTrue(result.report().notes().stream().anyMatch(n -> n.contains("queryText/responseText")),
                result.report().notes().toString());
    }
    @Test void convertsHistoricalCallToDbSelectPreservingIdsAndDependencies() {
        var input=json.readTree(WITH_CALL);
        String before=input.toString();
        var result=migration.migrate(input);
        assertEquals(before,input.toString());
        var actions=result.document().get("components").get(1).get("behaviors").get(0).get("actions");
        assertEquals("call",actions.get(0).get("id").asText());
        assertEquals("DB_SELECT",actions.get(0).get("kind").asText());
        assertEquals("data",actions.get(0).get("connectionId").asText());
        assertEquals("default",actions.get(0).get("collection").asText());
        assertEquals("[\"call\"]",actions.get(1).get("dependsOn").toString());
        var data=result.document().get("components").get(2).get("dataProperties");
        assertEquals(1000,data.get("connectionPoolSize").asInt());
        assertEquals(1000,data.get("maxConcurrentOperations").asInt());
        assertTrue(result.report().notes().stream().anyMatch(n -> n.contains("service/b/call CALL -> DB_SELECT")),
                result.report().notes().toString());
    }
    @Test void blocksCallWithTimeoutBecauseTimingWouldChange() {
        var input=json.readTree(WITH_CALL.replace("\"connectionId\":\"data\"}","\"connectionId\":\"data\",\"timeoutMs\":5}"));
        String before=input.toString();
        var error=assertThrows(br.com.archlab.api.architecture.LegacyDatabaseMigrationV6.AmbiguousMigrationException.class,
                ()->migration.migrate(input));
        assertEquals("HISTORICAL_CALL_TIMEOUT",error.reason());
        assertEquals("components[1].behaviors[0].actions[0]",error.path());
        assertEquals(before,input.toString());
    }
    @Test void blocksNonQueryConnectionToDatabase() {
        var input=json.readTree(BASE.replace("\"targetId\":\"db\",\"integration\":{\"kind\":\"DATABASE_QUERY\"}",
                "\"targetId\":\"db\",\"integration\":{\"kind\":\"HTTP_REST\"}"));
        var error=assertThrows(br.com.archlab.api.architecture.LegacyDatabaseMigrationV6.AmbiguousMigrationException.class,
                ()->migration.migrate(input));
        assertEquals("NON_QUERY_CONNECTION",error.reason());
    }
}
