package br.com.archlab.api;

import br.com.archlab.api.v2.V2RequestMapper;
import br.com.archlab.simulation.core.v41.UnifiedSimulationV41;
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
    @Test void legacyAndMigratedScenariosHaveEquivalentVirtualResults() {
        String scenario="\"scenario\":{\"entry\":{\"componentId\":\"client\",\"connectionId\":\"in\",\"operation\":\"GET /one\"}}";
        var original=json.readTree("{\"architecture\":"+OLD+","+scenario+"}");
        var converted=migration.migrate(original).document();
        var mapper=new V2RequestMapper();
        var before=mapper.canonicalArchitecture(original.get("architecture"));
        var after=mapper.canonicalArchitecture(converted.get("architecture"));
        var beforeRun=new UnifiedSimulationV41(UnifiedPlanV41.parse(original,before).toExecutionPlan()).run();
        var afterRun=new UnifiedSimulationV41(UnifiedPlanV41.parse(converted,after).toExecutionPlan()).run();
        assertEquals(beforeRun,afterRun);
    }
    @Test void preservesBrokerDeliveryAndDatabaseConnections() {
        var input=json.readTree("""
            {"components":[
              {"id":"client","type":"CLIENT","displayName":"Client"},
              {"id":"producer","type":"API","displayName":"Producer","behaviors":[
                {"id":"request","trigger":"REQUEST_RECEIVED","actions":[
                  {"id":"publish","kind":"PUBLISH","connectionId":"producer-broker","destinationName":"jobs"}]}]},
              {"id":"broker","type":"BROKER","displayName":"Broker",
               "destinations":[{"name":"jobs","kind":"QUEUE"}],
               "subscriptions":[{"destinationName":"jobs","consumerComponentId":"consumer","connectionId":"broker-consumer"}]},
              {"id":"consumer","type":"API","displayName":"Consumer","behaviors":[
                {"id":"consume","trigger":"MESSAGE_CONSUMED","match":{"destinationName":"jobs"},
                 "actions":[{"id":"read","kind":"CALL","connectionId":"consumer-db"}]}]},
              {"id":"database","type":"DATABASE","displayName":"Database","processingMs":3}
            ],"connections":[
              {"id":"in","sourceId":"client","targetId":"producer","latencyMs":1,
               "integration":{"kind":"HTTP_REST","request":{"method":"POST","path":"/jobs"}}},
              {"id":"producer-broker","sourceId":"producer","targetId":"broker","latencyMs":1,
               "integration":{"kind":"QUEUE","queueName":"jobs"}},
              {"id":"broker-consumer","sourceId":"broker","targetId":"consumer","latencyMs":1,
               "integration":{"kind":"QUEUE","queueName":"jobs"}},
              {"id":"consumer-db","sourceId":"consumer","targetId":"database","latencyMs":1,
               "integration":{"kind":"DATABASE_QUERY"}}
            ]}
            """);
        var before=input.toString();
        var result=migration.migrate(input);
        assertEquals(2,result.report().convertedCount());
        assertEquals("producer-broker",result.document().get("connections").get(1).get("id").asText());
        assertEquals("broker-consumer",result.document().get("connections").get(2).get("id").asText());
        assertEquals("consumer-db",result.document().get("connections").get(3).get("id").asText());
        assertEquals(before,input.toString());
    }
}
