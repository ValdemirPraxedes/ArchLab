package br.com.archlab.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DataActionsV6IntegrationTest {
    @Value("${local.server.port}") int port;
    private final HttpClient client=HttpClient.newHttpClient();
    private final JsonMapper json=new JsonMapper();

    private HttpResponse<String> post(String path,String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:"+port+path))
                .header("Content-Type","application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    private String scenario(String type,String actions) {
        return """
            {"architecture":{"components":[
              {"id":"browser","type":"WEB_BROWSER","displayName":"Browser"},
              {"id":"service","type":"SERVICE","displayName":"Service","behaviors":[
                {"id":"handle","trigger":"REQUEST_RECEIVED","actions":%s}]},
              {"id":"db","type":"%s","displayName":"Data","dataProperties":{
                "initialRecordCount":0,"averageRecordBytes":128,"readLatencyMs":2,
                "writeLatencyMs":3,"maxConcurrentOperations":1,"connectionPoolSize":1,"queueCapacity":2}}
            ],"connections":[
              {"id":"entry","sourceId":"browser","targetId":"service","latencyMs":1,
               "integration":{"kind":"HTTP_REST"}},
              {"id":"data","sourceId":"service","targetId":"db","latencyMs":1,
               "integration":{"kind":"DATABASE_QUERY"}}
            ]},"scenario":{"entry":{"componentId":"browser","connectionId":"entry"}}}
            """.formatted(actions,type);
    }
    @Test void relationalInsertIsVisibleToLaterSelectAndRunIsIsolated() throws Exception {
        String body=scenario("RELATIONAL_DATABASE","""
            [{"id":"insert","kind":"DB_INSERT","connectionId":"data","collection":"orders","key":"one","outputRef":"w"},
             {"id":"select","kind":"DB_SELECT","connectionId":"data","collection":"orders","key":"one","dependsOn":["insert"],"outputRef":"r"},
             {"id":"respond","kind":"RESPOND","dependsOn":["select"]}]
            """);
        var response=post("/api/v1/simulations",body);
        assertEquals(200,response.statusCode(),response.body());
        JsonNode root=json.readTree(response.body());
        assertEquals(2,root.get("dataOutcomes").size(),response.body());
        assertEquals("INSERTED",root.get("dataOutcomes").get(0).get("resultCode").asText());
        assertEquals("FOUND",root.get("dataOutcomes").get(1).get("resultCode").asText());
        assertEquals(response.body(),post("/api/v1/simulations",body).body());
    }
    @Test void incompatibleActionIsRejectedBeforeExecution() throws Exception {
        String body=scenario("KEY_VALUE_STORE","""
            [{"id":"select","kind":"DB_SELECT","connectionId":"data","collection":"items"}]
            """);
        var response=post("/api/v1/scenarios/validate",body);
        assertEquals(200,response.statusCode());
        assertTrue(response.body().contains("DATA_ACTION_INCOMPATIBLE"),response.body());
    }
    @Test void relationalUpdateDeleteAndDuplicateKeyHaveObservableResults() throws Exception {
        String body=scenario("RELATIONAL_DATABASE","""
            [{"id":"insert","kind":"DB_INSERT","connectionId":"data","key":"one"},
             {"id":"update","kind":"DB_UPDATE","connectionId":"data","key":"one","dependsOn":["insert"]},
             {"id":"delete","kind":"DB_DELETE","connectionId":"data","key":"one","dependsOn":["update"]},
             {"id":"read","kind":"DB_SELECT","connectionId":"data","collection":"default","key":"one","dependsOn":["delete"]}]
            """);
        var response=post("/api/v1/simulations",body);
        assertEquals(200,response.statusCode(),response.body());
        JsonNode outcomes=json.readTree(response.body()).get("dataOutcomes");
        assertEquals("INSERTED",outcomes.get(0).get("resultCode").asText());
        assertEquals("UPDATED",outcomes.get(1).get("resultCode").asText());
        assertEquals("DELETED",outcomes.get(2).get("resultCode").asText());
        assertEquals("NOT_FOUND",outcomes.get(3).get("resultCode").asText());
        String duplicate=scenario("RELATIONAL_DATABASE","""
            [{"id":"insert","kind":"DB_INSERT","connectionId":"data","key":"one"},
             {"id":"duplicate","kind":"DB_INSERT","connectionId":"data","key":"one","dependsOn":["insert"]}]
            """);
        var duplicateResponse=post("/api/v1/simulations",duplicate);
        assertEquals(200,duplicateResponse.statusCode(),duplicateResponse.body());
        JsonNode duplicateOutcomes=json.readTree(duplicateResponse.body()).get("dataOutcomes");
        assertEquals("FAILED",duplicateOutcomes.get(1).get("resultCode").asText());
        assertEquals("DUPLICATE_KEY",duplicateOutcomes.get(1).get("errorCode").asText());
    }
    @Test void bulkInsertRejectsAmbiguousSingleKey() throws Exception {
        String body=scenario("RELATIONAL_DATABASE","""
            [{"id":"bulk","kind":"DB_BULK_INSERT","connectionId":"data","recordCount":3,"key":"one"}]
            """);
        var response=post("/api/v1/scenarios/validate",body);
        assertEquals(200,response.statusCode(),response.body());
        assertTrue(response.body().contains("INVALID_VIRTUAL_RESOURCE_OPERATION"),response.body());
    }
    @Test void keyValueMissPutHitAndDocumentFindAreObservable() throws Exception {
        String kv=scenario("KEY_VALUE_STORE","""
            [{"id":"miss","kind":"DB_GET","connectionId":"data","key":"k"},
             {"id":"put","kind":"DB_PUT","connectionId":"data","key":"k","dependsOn":["miss"]},
             {"id":"hit","kind":"DB_GET","connectionId":"data","key":"k","dependsOn":["put"]}]
            """);
        JsonNode result=json.readTree(post("/api/v1/simulations",kv).body());
        assertEquals("NOT_FOUND",result.get("dataOutcomes").get(0).get("resultCode").asText());
        assertEquals("STORED",result.get("dataOutcomes").get(1).get("resultCode").asText());
        assertEquals("FOUND",result.get("dataOutcomes").get(2).get("resultCode").asText());
        String document=scenario("DOCUMENT_DATABASE","""
            [{"id":"put","kind":"DB_PUT","connectionId":"data","key":"doc-1","field":"category","equals":"a"},
             {"id":"find","kind":"DB_FIND","connectionId":"data","field":"category","equals":"a","dependsOn":["put"]}]
            """);
        result=json.readTree(post("/api/v1/simulations",document).body());
        assertEquals("FOUND",result.get("dataOutcomes").get(1).get("resultCode").asText());
        assertEquals(1,result.get("dataOutcomes").get(1).get("readCount").asInt());
    }
    @Test void etlExtractTransformLoadConservesCount() throws Exception {
        String body="""
            {"architecture":{"components":[
              {"id":"browser","type":"WEB_BROWSER","displayName":"Browser"},
              {"id":"etl","type":"ETL_PIPELINE","displayName":"ETL","batchSize":10,"maxBatches":1,
               "behaviors":[{"id":"run","trigger":"REQUEST_RECEIVED","actions":[
                 {"id":"extract","kind":"ETL_EXTRACT","connectionId":"source-edge","maxRecords":10,"outputRef":"raw"},
                 {"id":"transform","kind":"ETL_TRANSFORM","connectionId":"transform-edge","inputRef":"raw","transform":"IDENTITY",
                  "dependsOn":["extract"],"outputRef":"clean"},
                 {"id":"load","kind":"ETL_LOAD","connectionId":"target-edge","inputRef":"clean",
                  "dependsOn":["transform"]}]}]},
              {"id":"source","type":"DOCUMENT_DATABASE","displayName":"Source","dataProperties":{
                "initialRecordCount":3,"averageRecordBytes":128,"readLatencyMs":2,"writeLatencyMs":3,
                "maxConcurrentOperations":1,"connectionPoolSize":1,"queueCapacity":2}},
              {"id":"transformer","type":"DATA_TRANSFORMER","displayName":"Transformer",
               "transformCostPerRecordMs":4},
              {"id":"target","type":"RELATIONAL_DATABASE","displayName":"Target","dataProperties":{
                "initialRecordCount":0,"averageRecordBytes":128,"readLatencyMs":2,"writeLatencyMs":3,
                "maxConcurrentOperations":1,"connectionPoolSize":1,"queueCapacity":2}}
            ],"connections":[
              {"id":"entry","sourceId":"browser","targetId":"etl","latencyMs":1,"integration":{"kind":"HTTP_REST"}},
              {"id":"source-edge","sourceId":"etl","targetId":"source","latencyMs":1,"integration":{"kind":"DATABASE_QUERY"}},
              {"id":"transform-edge","sourceId":"etl","targetId":"transformer","latencyMs":1,"integration":{"kind":"HTTP_REST"}},
              {"id":"target-edge","sourceId":"etl","targetId":"target","latencyMs":1,"integration":{"kind":"DATABASE_QUERY"}}
            ]},"scenario":{"entry":{"componentId":"browser","connectionId":"entry"}}}
            """;
        var response=post("/api/v1/simulations",body);
        assertEquals(200,response.statusCode(),response.body());
        JsonNode outcomes=json.readTree(response.body()).get("dataOutcomes");
        assertEquals(3,outcomes.size(),response.body());
        assertEquals(3,outcomes.get(0).get("readCount").asInt());
        assertEquals(3,outcomes.get(1).get("writtenCount").asInt());
        assertEquals("transformer",outcomes.get(1).get("resourceComponentId").asText());
        assertEquals(12,outcomes.get(1).get("processingMs").asInt());
        assertEquals(3,outcomes.get(2).get("writtenCount").asInt());
        String failing=body.replace("\"transform\":\"IDENTITY\",",
                "\"transform\":\"IDENTITY\",\"simulateFailure\":true,");
        var failedResponse=post("/api/v1/simulations",failing);
        assertEquals(200,failedResponse.statusCode(),failedResponse.body());
        JsonNode failed=json.readTree(failedResponse.body()).get("dataOutcomes");
        assertEquals("FAILED",failed.get(1).get("status").asText());
        assertEquals("ETL_TRANSFORM_FAILED",failed.get(1).get("errorCode").asText());
        assertEquals("SKIPPED",failed.get(2).get("status").asText());
        assertEquals(0,failed.get(2).get("writtenCount").asInt());
        var filteredResponse=post("/api/v1/simulations",body.replace("\"transform\":\"IDENTITY\"",
                "\"transform\":\"FILTER_VALID\",\"invalidRecordCount\":1"));
        assertEquals(200,filteredResponse.statusCode(),filteredResponse.body());
        JsonNode filtered=json.readTree(filteredResponse.body()).get("dataOutcomes");
        assertEquals(3,filtered.get(1).get("readCount").asInt());
        assertEquals(2,filtered.get(1).get("writtenCount").asInt());
        assertEquals(1,filtered.get(1).get("rejectedCount").asInt());
        assertEquals(2,filtered.get(2).get("writtenCount").asInt());
        var normalizedResponse=post("/api/v1/simulations",body.replace("\"transform\":\"IDENTITY\"",
                "\"transform\":\"NORMALIZE_METADATA\""));
        assertEquals(200,normalizedResponse.statusCode(),normalizedResponse.body());
        JsonNode normalized=json.readTree(normalizedResponse.body()).get("dataOutcomes");
        assertEquals(3,normalized.get(1).get("normalizedCount").asInt());
        assertEquals(3,normalized.get(2).get("normalizedCount").asInt());
        var invalidCount=post("/api/v1/simulations",body.replace("\"transform\":\"IDENTITY\"",
                "\"transform\":\"FILTER_VALID\",\"invalidRecordCount\":4"));
        assertEquals(200,invalidCount.statusCode(),invalidCount.body());
        JsonNode invalidOutcomes=json.readTree(invalidCount.body()).get("dataOutcomes");
        assertEquals("ETL_INVALID_REJECTION_COUNT",invalidOutcomes.get(1).get("errorCode").asText());
        assertEquals("SKIPPED",invalidOutcomes.get(2).get("status").asText());
        var missingCount=post("/api/v1/scenarios/validate",body.replace("\"transform\":\"IDENTITY\"",
                "\"transform\":\"FILTER_VALID\""));
        assertEquals(200,missingCount.statusCode(),missingCount.body());
        assertTrue(missingCount.body().contains("DATA_LIMIT_EXCEEDED"),missingCount.body());
    }
    @Test void poolOneQueuesConcurrentWritesWithoutLosingEither() throws Exception {
        String body=scenario("RELATIONAL_DATABASE","""
            [{"id":"first","kind":"DB_INSERT","connectionId":"data","key":"one"},
             {"id":"second","kind":"DB_INSERT","connectionId":"data","key":"two"}]
            """);
        var response=post("/api/v1/simulations",body);
        assertEquals(200,response.statusCode(),response.body());
        JsonNode outcomes=json.readTree(response.body()).get("dataOutcomes");
        assertEquals(2,outcomes.size(),response.body());
        assertEquals(0,outcomes.get(0).get("queueWaitMs").asLong());
        assertTrue(outcomes.get(1).get("queueWaitMs").asLong()>0,response.body());
        assertEquals("INSERTED",outcomes.get(1).get("resultCode").asText());
    }
    @Test void queuedOperationTimesOutWithoutCommittingWrite() throws Exception {
        String body=scenario("RELATIONAL_DATABASE","""
            [{"id":"first","kind":"DB_INSERT","connectionId":"data","key":"one"},
             {"id":"second","kind":"DB_INSERT","connectionId":"data","key":"two","timeoutMs":1}]
            """);
        var response=post("/api/v1/simulations",body);
        assertEquals(200,response.statusCode(),response.body());
        JsonNode outcomes=json.readTree(response.body()).get("dataOutcomes");
        assertEquals(2,outcomes.size(),response.body());
        JsonNode timedOut=outcomes.get(0);
        assertEquals("second",timedOut.get("actionId").asText());
        assertEquals("TIMED_OUT",timedOut.get("status").asText());
        assertEquals(0,timedOut.get("writtenCount").asInt());
    }
    @Test void processingTimeoutLeavesNoVisibleRecord() throws Exception {
        String body=scenario("KEY_VALUE_STORE","""
            [{"id":"put","kind":"DB_PUT","connectionId":"data","key":"k","timeoutMs":1},
             {"id":"wait","kind":"PROCESS","durationMs":4},
             {"id":"read","kind":"DB_GET","connectionId":"data","key":"k","dependsOn":["wait"]}]
            """);
        var response=post("/api/v1/simulations",body);
        assertEquals(200,response.statusCode(),response.body());
        JsonNode outcomes=json.readTree(response.body()).get("dataOutcomes");
        assertEquals("TIMED_OUT",outcomes.get(0).get("resultCode").asText());
        assertEquals("NOT_FOUND",outcomes.get(1).get("resultCode").asText());
    }
    @Test void resourceLimitsRejectOversizedInitialDataset() throws Exception {
        String body=scenario("KEY_VALUE_STORE","""
            [{"id":"get","kind":"DB_GET","connectionId":"data","key":"k"}]
            """).replace("\"initialRecordCount\":0,\"averageRecordBytes\":128",
                    "\"initialRecordCount\":1000000,\"averageRecordBytes\":1048576");
        var response=post("/api/v1/architectures/validate",json.readTree(body).get("architecture").toString());
        assertEquals(200,response.statusCode());
        assertTrue(response.body().contains("DATA_LIMIT_EXCEEDED"),response.body());
    }
    @Test void etlLoadWithoutDependentProducerIsRejected() throws Exception {
        String body="""
            {"architecture":{"components":[
              {"id":"browser","type":"WEB_BROWSER","displayName":"Browser"},
              {"id":"etl","type":"ETL_PIPELINE","displayName":"ETL","behaviors":[
                {"id":"run","trigger":"REQUEST_RECEIVED","actions":[
                  {"id":"load","kind":"ETL_LOAD","connectionId":"data","inputRef":"missing"}]}]},
              {"id":"db","type":"RELATIONAL_DATABASE","displayName":"Data","dataProperties":{
                "initialRecordCount":0,"averageRecordBytes":128,"readLatencyMs":2,"writeLatencyMs":3,
                "maxConcurrentOperations":1,"connectionPoolSize":1,"queueCapacity":2}}
            ],"connections":[
              {"id":"entry","sourceId":"browser","targetId":"etl","integration":{"kind":"HTTP_REST"}},
              {"id":"data","sourceId":"etl","targetId":"db","integration":{"kind":"DATABASE_QUERY"}}
            ]},"scenario":{"entry":{"componentId":"browser","connectionId":"entry"}}}
            """;
        var response=post("/api/v1/scenarios/validate",body);
        assertEquals(200,response.statusCode());
        assertTrue(response.body().contains("DATA_OUTPUT_REF_INVALID"),response.body());
    }
    @Test void versionedCanonicalArchitectureRejectsLegacyDatabase() throws Exception {
        String body="""
            {"schemaVersion":"0.6","components":[
              {"id":"db","type":"DATABASE","displayName":"Legacy","processingMs":5}
            ],"connections":[]}
            """;
        var response=post("/api/v1/architectures/validate",body);
        assertEquals(400,response.statusCode(),response.body());
        assertTrue(response.body().contains("INVALID_REQUEST"),response.body());
    }
    @Test void explicitMigrationEndpointReturnsPreviewAndBlocksAmbiguousQuery() throws Exception {
        String old="""
            {"components":[
              {"id":"client","type":"CLIENT","displayName":"Client"},
              {"id":"service","type":"SERVICE","displayName":"Service"},
              {"id":"db","type":"DATABASE","displayName":"DB","processingMs":3}
            ],"connections":[
              {"id":"entry","sourceId":"client","targetId":"service","integration":{"kind":"HTTP_REST"}},
              {"id":"data","sourceId":"service","targetId":"db","integration":{"kind":"DATABASE_QUERY"}}
            ]}
            """;
        var migrated=post("/api/v1/architectures/migrate-v6",old);
        assertEquals(200,migrated.statusCode(),migrated.body());
        JsonNode document=json.readTree(migrated.body()).get("document");
        assertEquals("0.6",document.get("schemaVersion").asText());
        assertEquals("RELATIONAL_DATABASE",document.get("components").get(2).get("type").asText());
        var ambiguous=post("/api/v1/architectures/migrate-v6",old.replace(
                "\"kind\":\"DATABASE_QUERY\"","\"kind\":\"DATABASE_QUERY\",\"queryText\":\"SELECT 1\""));
        assertEquals(400,ambiguous.statusCode(),ambiguous.body());
    }

    // ---- v0.6 continuation (Claude): V6-01, V6-09, V6-10, V6-11, V6-12 ----
    /** Normative matrix shared with the editor (docs/api/examples/v0.6/capability-matrix-v6.json), by target type. */
    private java.util.Map<String,java.util.Set<String>> matrix() throws Exception {
        JsonNode actions=json.readTree(java.nio.file.Files.readString(java.nio.file.Path.of(
                "..","..","docs","api","examples","v0.6","capability-matrix-v6.json"))).get("actions");
        java.util.Map<String,java.util.Set<String>> byType=new java.util.TreeMap<>();
        for (var entry:actions.properties())
            for (JsonNode type:entry.getValue())
                byType.computeIfAbsent(type.asText(),k -> new java.util.TreeSet<>()).add(entry.getKey());
        return byType;
    }
    private static String minimalAction(String kind) {
        String required=switch(kind) {
            case "DB_SELECT" -> "\"collection\":\"c\"";
            case "DB_FIND" -> "\"field\":\"f\",\"equals\":\"v\"";
            case "DB_BULK_INSERT" -> "\"recordCount\":2";
            default -> "\"key\":\"k\"";
        };
        return "[{\"id\":\"a\",\"kind\":\""+kind+"\",\"connectionId\":\"data\","+required+"}]";
    }
    @Test void fullTypeByActionMatrixIsEnforcedBeforeExecution() throws Exception {
        java.util.List<String> kinds=java.util.List.of("DB_SELECT","DB_INSERT","DB_UPDATE","DB_DELETE",
                "DB_GET","DB_PUT","DB_FIND","DB_BULK_INSERT");
        var matrix=matrix();
        assertEquals(java.util.Set.of("RELATIONAL_DATABASE","DOCUMENT_DATABASE","KEY_VALUE_STORE","DATA_TRANSFORMER"),
                matrix.keySet());
        for (var entry:matrix.entrySet()) for (String kind:kinds) {
            if ("DATA_TRANSFORMER".equals(entry.getKey())) continue;
            var response=post("/api/v1/scenarios/validate",scenario(entry.getKey(),minimalAction(kind)));
            assertEquals(200,response.statusCode(),response.body());
            boolean rejected=response.body().contains("DATA_ACTION_INCOMPATIBLE");
            assertEquals(!entry.getValue().contains(kind),rejected,entry.getKey()+" x "+kind+": "+response.body());
            if (!rejected) assertTrue(json.readTree(response.body()).get("valid").asBoolean(),
                    entry.getKey()+" x "+kind+": "+response.body());
        }
    }
    @Test void etlExtractAndLoadAcceptEveryDataResourceTypeOfTheMatrix() throws Exception {
        var matrix=matrix();
        for (String type:java.util.List.of("RELATIONAL_DATABASE","DOCUMENT_DATABASE","KEY_VALUE_STORE")) {
            assertTrue(matrix.get(type).containsAll(java.util.Set.of("ETL_EXTRACT","ETL_LOAD")),type);
            String asSource=etl(3,10,1,"").replace("\"id\":\"source\",\"type\":\"DOCUMENT_DATABASE\"",
                    "\"id\":\"source\",\"type\":\""+type+"\"");
            String asTarget=etl(3,10,1,"").replace("\"id\":\"target\",\"type\":\"RELATIONAL_DATABASE\"",
                    "\"id\":\"target\",\"type\":\""+type+"\"");
            for (String body:java.util.List.of(asSource,asTarget)) {
                assertTrue(body.contains("\"type\":\""+type+"\""),type);
                var validation=post("/api/v1/scenarios/validate",body);
                assertTrue(json.readTree(validation.body()).get("valid").asBoolean(),type+": "+validation.body());
                JsonNode outcomes=json.readTree(post("/api/v1/simulations",body).body()).get("dataOutcomes");
                assertEquals("LOADED",outcomes.get(outcomes.size()-1).get("resultCode").asText(),type);
                assertEquals(3,outcomes.get(outcomes.size()-1).get("writtenCount").asInt(),type);
            }
        }
    }
    @Test void documentGetUpdateDeleteAndFindReflectVirtualState() throws Exception {
        String body=scenario("DOCUMENT_DATABASE","""
            [{"id":"p2","kind":"DB_PUT","connectionId":"data","key":"doc-2","field":"category","equals":"a"},
             {"id":"p1","kind":"DB_PUT","connectionId":"data","key":"doc-1","field":"category","equals":"a","dependsOn":["p2"]},
             {"id":"p3","kind":"DB_PUT","connectionId":"data","key":"doc-3","field":"category","equals":"b","dependsOn":["p1"]},
             {"id":"find1","kind":"DB_FIND","connectionId":"data","field":"category","equals":"a","dependsOn":["p3"]},
             {"id":"get","kind":"DB_GET","connectionId":"data","key":"doc-1","dependsOn":["find1"]},
             {"id":"update","kind":"DB_UPDATE","connectionId":"data","key":"doc-1","dependsOn":["get"]},
             {"id":"delete","kind":"DB_DELETE","connectionId":"data","key":"doc-2","dependsOn":["update"]},
             {"id":"find2","kind":"DB_FIND","connectionId":"data","field":"category","equals":"a","dependsOn":["delete"]},
             {"id":"limited","kind":"DB_FIND","connectionId":"data","field":"category","equals":"a","limit":1,"dependsOn":["find2"]},
             {"id":"gone","kind":"DB_GET","connectionId":"data","key":"doc-2","dependsOn":["limited"]},
             {"id":"missingUpdate","kind":"DB_UPDATE","connectionId":"data","key":"doc-9","dependsOn":["gone"]}]
            """);
        var response=post("/api/v1/simulations",body);
        assertEquals(200,response.statusCode(),response.body());
        JsonNode o=json.readTree(response.body()).get("dataOutcomes");
        assertEquals(11,o.size(),response.body());
        assertEquals(2,o.get(3).get("readCount").asInt());
        assertEquals("FOUND",o.get(4).get("resultCode").asText());
        assertEquals("UPDATED",o.get(5).get("resultCode").asText());
        assertEquals("DELETED",o.get(6).get("resultCode").asText());
        // update without a field keeps doc-1 findable; doc-2 was removed
        assertEquals(1,o.get(7).get("readCount").asInt(),response.body());
        assertEquals(1,o.get(8).get("readCount").asInt());
        assertEquals("NOT_FOUND",o.get(9).get("resultCode").asText());
        assertEquals("NOT_FOUND",o.get(10).get("resultCode").asText());
        assertEquals(0,o.get(10).get("writtenCount").asInt());
        for (int i=1;i<o.size();i++)
            assertTrue(o.get(i).get("timestampMs").asLong()>=o.get(i-1).get("timestampMs").asLong(),"ordered outcomes");
        assertEquals(response.body(),post("/api/v1/simulations",body).body());
    }
    @Test void keyValueDeleteHasNoImplicitTtl() throws Exception {
        String body=scenario("KEY_VALUE_STORE","""
            [{"id":"put","kind":"DB_PUT","connectionId":"data","key":"k"},
             {"id":"idle","kind":"PROCESS","durationMs":600000,"dependsOn":["put"]},
             {"id":"still","kind":"DB_GET","connectionId":"data","key":"k","dependsOn":["idle"]},
             {"id":"delete","kind":"DB_DELETE","connectionId":"data","key":"k","dependsOn":["still"]},
             {"id":"after","kind":"DB_GET","connectionId":"data","key":"k","dependsOn":["delete"]},
             {"id":"again","kind":"DB_DELETE","connectionId":"data","key":"k","dependsOn":["after"]}]
            """);
        var response=post("/api/v1/simulations",body);
        assertEquals(200,response.statusCode(),response.body());
        JsonNode o=json.readTree(response.body()).get("dataOutcomes");
        assertEquals("STORED",o.get(0).get("resultCode").asText());
        assertEquals("FOUND",o.get(1).get("resultCode").asText(),"no implicit TTL after 10 virtual minutes");
        assertEquals("DELETED",o.get(2).get("resultCode").asText());
        assertEquals(1,o.get(2).get("writtenCount").asInt());
        assertEquals("NOT_FOUND",o.get(3).get("resultCode").asText());
        assertEquals("NOT_FOUND",o.get(4).get("resultCode").asText());
        var ttl=post("/api/v1/scenarios/validate",scenario("KEY_VALUE_STORE","""
            [{"id":"put","kind":"DB_PUT","connectionId":"data","key":"k","ttlMs":10}]
            """));
        // ttlMs belongs to CACHE (v0.5); a KEY_VALUE_STORE Action carrying it is rejected at decode time
        assertEquals(400,ttl.statusCode(),"ttlMs is not a KEY_VALUE_STORE property: "+ttl.body());
        assertTrue(ttl.body().contains("INVALID_REQUEST"),ttl.body());
    }
    @Test void fullDataQueueRejectsWithDataQueueFull() throws Exception {
        // pool 1 + queueCapacity 2: one active, two waiting, the fourth is rejected deterministically
        String body=scenario("RELATIONAL_DATABASE","""
            [{"id":"w1","kind":"DB_INSERT","connectionId":"data","key":"1"},
             {"id":"w2","kind":"DB_INSERT","connectionId":"data","key":"2"},
             {"id":"w3","kind":"DB_INSERT","connectionId":"data","key":"3"},
             {"id":"w4","kind":"DB_INSERT","connectionId":"data","key":"4"},
             {"id":"count","kind":"DB_SELECT","connectionId":"data","collection":"default","dependsOn":["w1","w2","w3"]}]
            """);
        var response=post("/api/v1/simulations",body);
        assertEquals(200,response.statusCode(),response.body());
        JsonNode o=json.readTree(response.body()).get("dataOutcomes");
        JsonNode rejected=null; int inserted=0;
        for (JsonNode outcome:o) {
            if ("DATA_QUEUE_FULL".equals(outcome.path("errorCode").asText())) rejected=outcome;
            if ("INSERTED".equals(outcome.get("resultCode").asText())) inserted++;
        }
        assertNotNull(rejected,response.body());
        assertEquals("w4",rejected.get("actionId").asText());
        assertEquals("REJECTED",rejected.get("status").asText());
        assertEquals(0,rejected.get("writtenCount").asInt());
        assertEquals(3,inserted,response.body());
        assertEquals(response.body(),post("/api/v1/simulations",body).body());
    }
    private String etl(int initial,int batchSize,int maxBatches,String extraExtract) {
        return """
            {"architecture":{"components":[
              {"id":"browser","type":"WEB_BROWSER","displayName":"Browser"},
              {"id":"etl","type":"ETL_PIPELINE","displayName":"ETL","batchSize":%d,"maxBatches":%d,
               "behaviors":[{"id":"run","trigger":"REQUEST_RECEIVED","actions":[
                 {"id":"extract","kind":"ETL_EXTRACT","connectionId":"source-edge","outputRef":"raw"%s},
                 {"id":"transform","kind":"ETL_TRANSFORM","connectionId":"transform-edge","inputRef":"raw",
                  "transform":"IDENTITY","dependsOn":["extract"],"outputRef":"clean"},
                 {"id":"load","kind":"ETL_LOAD","connectionId":"target-edge","inputRef":"clean","dependsOn":["transform"]}]}]},
              {"id":"source","type":"DOCUMENT_DATABASE","displayName":"Source","dataProperties":{
                "initialRecordCount":%d,"averageRecordBytes":64,"readLatencyMs":1,"writeLatencyMs":1,
                "maxConcurrentOperations":1,"connectionPoolSize":1,"queueCapacity":2}},
              {"id":"transformer","type":"DATA_TRANSFORMER","displayName":"Transformer","transformCostPerRecordMs":1},
              {"id":"target","type":"RELATIONAL_DATABASE","displayName":"Target","dataProperties":{
                "initialRecordCount":0,"averageRecordBytes":64,"readLatencyMs":1,"writeLatencyMs":1,
                "maxConcurrentOperations":1,"connectionPoolSize":1,"queueCapacity":2}}
            ],"connections":[
              {"id":"entry","sourceId":"browser","targetId":"etl","latencyMs":1,"integration":{"kind":"HTTP_REST"}},
              {"id":"source-edge","sourceId":"etl","targetId":"source","latencyMs":1,"integration":{"kind":"DATABASE_QUERY"}},
              {"id":"transform-edge","sourceId":"etl","targetId":"transformer","latencyMs":1,"integration":{"kind":"HTTP_REST"}},
              {"id":"target-edge","sourceId":"etl","targetId":"target","latencyMs":1,"integration":{"kind":"DATABASE_QUERY"}}
            ]},"scenario":{"entry":{"componentId":"browser","connectionId":"entry"}}}
            """.formatted(batchSize,maxBatches,extraExtract,initial);
    }
    @Test void etlPartitionsRecordsIntoFiniteBatches() throws Exception {
        var response=post("/api/v1/simulations",etl(25,10,3,""));
        assertEquals(200,response.statusCode(),response.body());
        JsonNode o=json.readTree(response.body()).get("dataOutcomes");
        assertEquals(3,o.size(),response.body());
        for (JsonNode outcome:o) assertEquals(3,outcome.get("batchCount").asInt(),response.body());
        assertEquals(10,o.get(0).get("batchSize").asInt());
        assertEquals(25,o.get(0).get("readCount").asInt());
        assertEquals("LOADED",o.get(2).get("resultCode").asText());
        assertEquals(25,o.get(2).get("writtenCount").asInt());
        // Action-level batchSize overrides the pipeline property
        JsonNode override=json.readTree(post("/api/v1/simulations",etl(25,10,3,",\"batchSize\":25")).body())
                .get("dataOutcomes");
        assertEquals(1,override.get(0).get("batchCount").asInt());
        assertEquals(25,override.get(2).get("writtenCount").asInt());
    }
    @Test void etlAboveMaxBatchesFailsControlledAndSkipsLoad() throws Exception {
        String body=etl(25,10,2,"");
        var response=post("/api/v1/simulations",body);
        assertEquals(200,response.statusCode(),response.body());
        JsonNode o=json.readTree(response.body()).get("dataOutcomes");
        assertEquals("LIMIT_EXCEEDED",o.get(0).get("resultCode").asText(),response.body());
        assertEquals("FAILED",o.get(0).get("status").asText());
        assertEquals("ETL_BATCH_LIMIT_EXCEEDED",o.get(0).get("errorCode").asText());
        assertEquals(0,o.get(0).get("batchCount").asInt());
        JsonNode last=o.get(o.size()-1);
        assertEquals("load",last.get("actionId").asText(),response.body());
        assertEquals("SKIPPED",last.get("status").asText());
        assertEquals(0,last.get("writtenCount").asInt());
        assertEquals(response.body(),post("/api/v1/simulations",body).body());
    }

    // ---- V6-13: dataOutcomes correlate with trace and timeline ----
    private void assertOutcomesCorrelate(JsonNode root,String body) {
        JsonNode trace=root.get("trace");
        java.util.Set<String> exchanges=new java.util.HashSet<>();
        for (JsonNode entry:trace) if ("MESSAGE_EVENT".equals(entry.get("type").asText()))
            exchanges.add(entry.path("exchangeId").asText());
        for (JsonNode outcome:root.get("dataOutcomes")) {
            boolean matched=false;
            for (JsonNode entry:trace)
                if ("ACTION_EVENT".equals(entry.get("type").asText())
                        && outcome.get("actionId").asText().equals(entry.get("actionId").asText())
                        && outcome.equals(entry.get("outcome"))) {
                    matched=true;
                    assertEquals(outcome.get("operationId").asText(),entry.get("operationId").asText(),body);
                    assertEquals(outcome.get("timestampMs").asLong(),entry.get("timestampMs").asLong(),body);
                }
            assertTrue(matched,"outcome without ACTION_EVENT: "+outcome);
            String op=outcome.get("operationId").asText();
            if (op.startsWith("exchange-") && !"SKIPPED".equals(outcome.get("status").asText()))
                assertTrue(exchanges.contains(op),"outcome operation "+op+" has no message exchange: "+body);
        }
    }
    @Test void everyDataOutcomeIsTraceableIncludingQueueRejectionsAndTimeouts() throws Exception {
        String crud=scenario("RELATIONAL_DATABASE","""
            [{"id":"w1","kind":"DB_INSERT","connectionId":"data","key":"1"},
             {"id":"w2","kind":"DB_INSERT","connectionId":"data","key":"2"},
             {"id":"w3","kind":"DB_INSERT","connectionId":"data","key":"3","timeoutMs":2},
             {"id":"w4","kind":"DB_INSERT","connectionId":"data","key":"4"},
             {"id":"read","kind":"DB_SELECT","connectionId":"data","collection":"default","key":"1","dependsOn":["w1"]}]
            """);
        var response=post("/api/v1/simulations",crud);
        assertEquals(200,response.statusCode(),response.body());
        JsonNode root=json.readTree(response.body());
        java.util.Set<String> errors=new java.util.HashSet<>();
        for (JsonNode o:root.get("dataOutcomes")) errors.add(o.path("errorCode").asText());
        assertTrue(errors.contains("DATA_QUEUE_FULL"),response.body());
        assertTrue(errors.contains("DATA_TIMEOUT"),response.body());
        assertOutcomesCorrelate(root,response.body());
        boolean dataActivity=false;
        for (JsonNode a:root.get("timeline")) dataActivity|="DATA_OPERATION".equals(a.path("phase").asText());
        assertTrue(dataActivity,"timeline must show DATA_OPERATION processing");
        JsonNode etl=json.readTree(post("/api/v1/simulations",etl(4,10,1,"")).body());
        assertOutcomesCorrelate(etl,etl.toString());
        String failing=etl(4,10,1,"").replace("\"transform\":\"IDENTITY\"","\"transform\":\"IDENTITY\",\"simulateFailure\":true");
        JsonNode failed=json.readTree(post("/api/v1/simulations",failing).body());
        assertEquals("SKIPPED",failed.get("dataOutcomes").get(failed.get("dataOutcomes").size()-1).get("status").asText());
        assertOutcomesCorrelate(failed,failed.toString());
    }
}
