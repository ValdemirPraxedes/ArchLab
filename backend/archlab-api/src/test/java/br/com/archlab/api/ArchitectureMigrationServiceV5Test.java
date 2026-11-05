package br.com.archlab.api;

import br.com.archlab.api.v2.V2RequestMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectureMigrationServiceV5Test {
    private final JsonMapper json=new JsonMapper();
    private final ArchitectureMigrationServiceV5 migration=new ArchitectureMigrationServiceV5(new V2RequestMapper());
    private static final String OLD="""
        {"components":[
          {"id":"client","type":"CLIENT","displayName":"Client"},
          {"id":"api-one","type":"API","displayName":"One","preProcessingMs":3,
           "behaviors":[{"id":"b1","trigger":"REQUEST_RECEIVED","actions":[
             {"id":"call","kind":"CALL","connectionId":"one-two"}]}]},
          {"id":"api-two","type":"API","displayName":"Two","postProcessingMs":2,
           "behaviors":[{"id":"b2","trigger":"REQUEST_RECEIVED","actions":[
             {"id":"respond","kind":"RESPOND","statusCode":200}]}]}
        ],"connections":[
          {"id":"in","sourceId":"client","targetId":"api-one","latencyMs":1,
           "integration":{"kind":"HTTP_REST","request":{"method":"GET","path":"/one"}}},
          {"id":"one-two","sourceId":"api-one","targetId":"api-two","latencyMs":1,
           "integration":{"kind":"HTTP_REST","request":{"method":"GET","path":"/two"}}}
        ]}
        """;

    @Test void validatesConvertedArchitectureWithoutChangingOriginal() {
        var input=json.readTree(OLD);
        var original=input.toString();
        var result=migration.migrate(input);
        assertEquals(2,result.report().convertedCount());
        assertEquals("SERVICE",result.document().get("components").get(1).get("type").asText());
        assertEquals("one-two",result.document().get("connections").get(1).get("id").asText());
        assertEquals(original,input.toString());
    }
    @Test void reportsInvalidMigrationAndPreservesInput() {
        var input=json.readTree(OLD.replace("\"targetId\":\"api-two\"",
                "\"targetId\":\"absent\""));
        var original=input.toString();
        var problem=assertThrows(ArchitectureMigrationServiceV5.MigrationException.class,
                () -> migration.migrate(input));
        assertFalse(problem.violations().isEmpty());
        assertEquals(original,input.toString());
    }
}
