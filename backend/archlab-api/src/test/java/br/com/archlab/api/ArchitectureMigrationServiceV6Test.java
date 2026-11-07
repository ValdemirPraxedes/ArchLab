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
    @Test void rejectsHistoricalQueryTextWithoutMutatingInput() {
        var input=json.readTree(BASE.replace("\"kind\":\"DATABASE_QUERY\"",
                "\"kind\":\"DATABASE_QUERY\",\"queryText\":\"SELECT * FROM t\""));
        String before=input.toString();
        assertThrows(IllegalArgumentException.class,()->migration.migrate(input));
        assertEquals(before,input.toString());
    }
    @Test void rejectsHistoricalCallWithoutMutatingInput() {
        var input=json.readTree(BASE.replace("\"displayName\":\"Service\"",
                "\"displayName\":\"Service\",\"behaviors\":[{\"id\":\"b\",\"trigger\":\"REQUEST_RECEIVED\",\"actions\":[{\"id\":\"call\",\"kind\":\"CALL\",\"connectionId\":\"data\"}]}]"));
        String before=input.toString();
        assertThrows(IllegalArgumentException.class,()->migration.migrate(input));
        assertEquals(before,input.toString());
    }
}
