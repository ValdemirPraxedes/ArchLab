package br.com.archlab.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class V2PublicContractTest {
    @Value("${local.server.port}") int port;
    @Autowired JsonMapper json;
    private final HttpClient client=HttpClient.newHttpClient();
    private static final String ARCH="""
        {"components":[{"id":"c","type":"CLIENT","displayName":"Client"},
          {"id":"a","type":"API","displayName":"API A","preProcessingMs":10,"postProcessingMs":5},
          {"id":"b","type":"API","displayName":"API B","preProcessingMs":7,"postProcessingMs":3},
          {"id":"d","type":"DATABASE","displayName":"Database","processingMs":20}],
         "connections":[
          {"id":"ca","sourceId":"c","targetId":"a","latencyMs":2,
           "integration":{"kind":"HTTP_REST","request":{"method":"POST","path":"/x"},"response":{"statusCode":500}}},
          {"id":"ab","sourceId":"a","targetId":"b","latencyMs":2,
           "integration":{"kind":"SOAP_HTTP","soapVersion":"1.2","requestEnvelope":"<Envelope/>"}},
          {"id":"bd","sourceId":"b","targetId":"d","latencyMs":2,
           "integration":{"kind":"DATABASE_QUERY","queryText":"SELECT 1"}}]}
        """;
    private static final String SCENARIO="""
        {"entryComponentId":"c","entryConnectionId":"ca",
         "apiExecutions":[{"componentId":"a","dependencyConnectionId":"ab"},
          {"componentId":"b","dependencyConnectionId":"bd"}]}
        """;
    private String request(String scenario) { return "{\"architecture\":"+ARCH+",\"scenario\":"+scenario+"}"; }
    private HttpResponse<String> post(String path,String jsonBody,String type) throws Exception {
        var req=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/api/v2/"+path))
            .header("Content-Type",type)
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody,StandardCharsets.UTF_8)).build();
        return client.send(req,HttpResponse.BodyHandlers.ofString());
    }
    private HttpResponse<String> post(String path,String body) throws Exception {
        return post(path,body,"application/json");
    }
    @Test void architectureAndScenarioValidationAreIndependentAnd200() throws Exception {
        var a=post("architectures/validate",ARCH);
        assertEquals(200,a.statusCode());
        assertTrue(json.readTree(a.body()).get("valid").asBoolean());
        var s=post("scenarios/validate",request(SCENARIO));
        assertEquals(200,s.statusCode());
        assertTrue(json.readTree(s.body()).get("valid").asBoolean());
    }
    @Test void nestedSimulationHasPublicTraceTimelineMetricsAndSnapshots() throws Exception {
        var response=post("simulations",request(SCENARIO));
        assertEquals(200,response.statusCode(),response.body());
        JsonNode root=json.readTree(response.body());
        assertEquals("COMPLETED",root.get("execution").get("status").asText());
        assertEquals("request-1",root.get("execution").get("requestId").asText());
        assertEquals(57,root.get("execution").get("totalDurationMs").asInt());
        assertEquals(11,root.get("timeline").size());
        assertEquals(3,root.get("messageRepresentations").size());
        assertEquals("HTTP_REST",root.get("messageRepresentations").get(0)
                .get("integration").get("kind").asText());
        assertEquals("SOAP_HTTP",root.get("messageRepresentations").get(1)
                .get("integration").get("kind").asText());
        assertEquals("DATABASE_QUERY",root.get("messageRepresentations").get(2)
                .get("integration").get("kind").asText());
        assertEquals(2,root.get("metrics").get("waitingByComponent").size());
        Set<String> types=new HashSet<>();
        for(int i=0;i<root.get("trace").size();i++){
            var t=root.get("trace").get(i);
            assertEquals(i+1,t.get("sequence").asInt());
            types.add(t.get("type").asText());
        }
        assertEquals(Set.of("MESSAGE_EVENT","PROCESSING_EVENT","STATE_TRANSITION"),types);
        assertEquals(500,root.get("messageRepresentations").get(0)
            .get("integration").get("response").get("statusCode").asInt());
    }
    @Test void semanticInvalidityReturnsValidation200ButSimulation422() throws Exception {
        String broken=SCENARIO.replace("\"entryComponentId\":\"c\"",
                "\"entryComponentId\":\"missing\"");
        var validation=post("scenarios/validate",request(broken));
        assertEquals(200,validation.statusCode());
        assertFalse(json.readTree(validation.body()).get("valid").asBoolean());
        var sim=post("simulations",request(broken));
        assertEquals(422,sim.statusCode());
        var problem=json.readTree(sim.body());
        assertEquals("SIMULATION_INVALID",problem.get("code").asText());
        assertTrue(problem.get("violations").size()>0);
        assertTrue(sim.headers().firstValue("content-type").orElse("").contains("application/problem+json"));
    }
    @Test void malformedAndUnknownPropertiesReturn400() throws Exception {
        assertEquals(400,post("architectures/validate","{bad").statusCode());
        assertEquals(400,post("architectures/validate",ARCH.replace("\"displayName\":\"Client\"",
                "\"displayName\":\"Client\",\"unknown\":1")).statusCode());
    }
    @Test void wrongContentTypeReturns415() throws Exception {
        assertEquals(415,post("architectures/validate",ARCH,"text/plain").statusCode());
    }
    @Test void oversizedV2JsonBodyReturns413ProblemDetails() throws Exception {
        var response=post("architectures/validate","x".repeat(1024*1024+1));
        assertEquals(413,response.statusCode());
        assertTrue(response.headers().firstValue("content-type").orElse("")
                .contains("application/problem+json"));
        assertEquals("PAYLOAD_TOO_LARGE",json.readTree(response.body()).get("code").asText());
    }

    @Test void onlyChosenPathIsSimulatedAndDbIsOptional() throws Exception {
        String scenario="{\"entryComponentId\":\"c\",\"entryConnectionId\":\"ca\",\"apiExecutions\":[]}";
        var response=post("simulations",request(scenario));
        assertEquals(200,response.statusCode(),response.body());
        var node=json.readTree(response.body());
        assertEquals(19,node.get("execution").get("totalDurationMs").asInt());
        assertEquals(1,node.get("messageRepresentations").size());
    }

    @Test void incomingOverrideChangesOnlyDestinationProcessingAndMetrics() throws Exception {
        String overridden=ARCH.replace("\"sourceId\":\"a\",\"targetId\":\"b\",\"latencyMs\":2,",
                "\"sourceId\":\"a\",\"targetId\":\"b\",\"latencyMs\":2,"
                +"\"apiProcessingOverride\":{\"preProcessingMs\":40,\"postProcessingMs\":15},");
        String request="{\"architecture\":"+overridden+",\"scenario\":"+SCENARIO+"}";
        var reply=post("simulations",request);
        assertEquals(200,reply.statusCode(),reply.body());
        var body=json.readTree(reply.body());
        assertEquals(102,body.get("execution").get("totalDurationMs").asInt());
        var processing=body.get("metrics").get("processingByComponent");
        boolean found=false;
        for(var p:processing) if("b".equals(p.get("componentId").asText())) {
            assertEquals(55,p.get("durationMs").asInt());
            found=true;
        }
        assertTrue(found);
    }
    @Test void partialOverrideIsRejectedAndInvalidDurationIsDomainViolation() throws Exception {
        String partial=ARCH.replace("\"sourceId\":\"a\",\"targetId\":\"b\",\"latencyMs\":2,",
                "\"sourceId\":\"a\",\"targetId\":\"b\",\"latencyMs\":2,"
                +"\"apiProcessingOverride\":{\"preProcessingMs\":40},");
        assertEquals(400,post("architectures/validate",partial).statusCode());
        String negative=ARCH.replace("\"sourceId\":\"a\",\"targetId\":\"b\",\"latencyMs\":2,",
                "\"sourceId\":\"a\",\"targetId\":\"b\",\"latencyMs\":2,"
                +"\"apiProcessingOverride\":{\"preProcessingMs\":-1,\"postProcessingMs\":0},");
        var response=post("architectures/validate",negative);
        assertEquals(200,response.statusCode());
        assertFalse(json.readTree(response.body()).get("valid").asBoolean());
    }
    @Test void overrideOnDatabaseConnectionIsRejected() throws Exception {
        String invalid=ARCH.replace("\"sourceId\":\"b\",\"targetId\":\"d\",\"latencyMs\":2,",
                "\"sourceId\":\"b\",\"targetId\":\"d\",\"latencyMs\":2,"
                +"\"apiProcessingOverride\":{\"preProcessingMs\":10,\"postProcessingMs\":5},");
        var response=post("architectures/validate",invalid);
        assertEquals(200,response.statusCode());
        assertFalse(json.readTree(response.body()).get("valid").asBoolean());
    }
    @Test void nonIntegralAndOutOfBoundsOverrideAreRejected() throws Exception {
        String target="\"sourceId\":\"a\",\"targetId\":\"b\",\"latencyMs\":2,";
        String partialPrefix=target+"\"apiProcessingOverride\":";
        String fractional=ARCH.replace(target,partialPrefix+
                "{\"preProcessingMs\":1.5,\"postProcessingMs\":0},");
        assertEquals(400,post("architectures/validate",fractional).statusCode());
        String excessive=ARCH.replace(target,partialPrefix+
                "{\"preProcessingMs\":1000000001,\"postProcessingMs\":0},");
        var validation=post("architectures/validate",excessive);
        assertEquals(200,validation.statusCode());
        assertFalse(json.readTree(validation.body()).get("valid").asBoolean());
        var rejected=post("simulations","{\"architecture\":"+excessive+
                ",\"scenario\":"+SCENARIO+"}");
        assertEquals(422,rejected.statusCode());
        assertTrue(json.readTree(rejected.body()).get("violations").size()>0);
    }
}
