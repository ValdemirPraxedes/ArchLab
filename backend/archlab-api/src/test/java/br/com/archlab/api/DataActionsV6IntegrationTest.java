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
                 {"id":"transform","kind":"ETL_TRANSFORM","inputRef":"raw","transform":"IDENTITY",
                  "dependsOn":["extract"],"outputRef":"clean"},
                 {"id":"load","kind":"ETL_LOAD","connectionId":"target-edge","inputRef":"clean",
                  "dependsOn":["transform"]}]}]},
              {"id":"source","type":"DOCUMENT_DATABASE","displayName":"Source","dataProperties":{
                "initialRecordCount":3,"averageRecordBytes":128,"readLatencyMs":2,"writeLatencyMs":3,
                "maxConcurrentOperations":1,"connectionPoolSize":1,"queueCapacity":2}},
              {"id":"target","type":"RELATIONAL_DATABASE","displayName":"Target","dataProperties":{
                "initialRecordCount":0,"averageRecordBytes":128,"readLatencyMs":2,"writeLatencyMs":3,
                "maxConcurrentOperations":1,"connectionPoolSize":1,"queueCapacity":2}}
            ],"connections":[
              {"id":"entry","sourceId":"browser","targetId":"etl","latencyMs":1,"integration":{"kind":"HTTP_REST"}},
              {"id":"source-edge","sourceId":"etl","targetId":"source","latencyMs":1,"integration":{"kind":"DATABASE_QUERY"}},
              {"id":"target-edge","sourceId":"etl","targetId":"target","latencyMs":1,"integration":{"kind":"DATABASE_QUERY"}}
            ]},"scenario":{"entry":{"componentId":"browser","connectionId":"entry"}}}
            """;
        var response=post("/api/v1/simulations",body);
        assertEquals(200,response.statusCode(),response.body());
        JsonNode outcomes=json.readTree(response.body()).get("dataOutcomes");
        assertEquals(3,outcomes.size(),response.body());
        assertEquals(3,outcomes.get(0).get("readCount").asInt());
        assertEquals(3,outcomes.get(1).get("writtenCount").asInt());
        assertEquals(3,outcomes.get(2).get("writtenCount").asInt());
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
}
