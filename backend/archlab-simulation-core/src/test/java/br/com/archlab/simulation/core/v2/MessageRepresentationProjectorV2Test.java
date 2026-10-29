package br.com.archlab.simulation.core.v2;

import br.com.archlab.simulation.core.engine.SimulationEngine;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MessageRepresentationProjectorV2Test {
    private static NestedExecutionResultV2 run(IntegrationV2 active, IntegrationV2 unused) {
        var components=List.<ComponentV2>of(new ClientV2("c","Client"),
                new ApiV2("a","A",2,3),new ApiV2("inactive","Inactive",50,50));
        var connections=List.of(new ConnectionV2("ca","c","a",1,active),
                new ConnectionV2("ci","c","inactive",1,unused));
        return new SimulationEngine().execute(new ArchitectureV2(components,connections),
                new ScenarioV2("c","ca",List.of()));
    }

    @Test void restDefaultsArePresentAndInactiveConnectionIsExcluded() {
        var r=run(new HttpRestIntegrationV2(null,null),
                new HttpRestIntegrationV2(null,null));
        assertEquals(1,r.messageRepresentations().size());
        var m=r.messageRepresentations().getFirst();
        assertEquals("ca",m.connectionId());
        var rest=(HttpRestIntegrationV2)m.integration();
        assertEquals(HttpMethodV2.GET,rest.request().method());
        assertEquals("/",rest.request().path());
        assertEquals(200,rest.response().statusCode());
        assertEquals(7,r.totalDurationMs());
    }

    @Test void http500IsDescriptiveAndNeverFailsEngine() {
        var r=run(new HttpRestIntegrationV2(new HttpRequestV2(HttpMethodV2.POST,
                "/test",Map.of("X-Test","yes"),"{}"),
                new HttpResponseV2(500,Map.of(),"Internal Error")),
                new HttpRestIntegrationV2(null,null));
        var rest=(HttpRestIntegrationV2)r.messageRepresentations().getFirst().integration();
        assertEquals(500,rest.response().statusCode());
        assertEquals(7,r.totalDurationMs());
    }

    @Test void soapFaultIsIllustrativeAndXmlIsPreserved() {
        String envelope="<env:Envelope xmlns:env=\"http://www.w3.org/2003/05/soap-envelope\"><env:Body><env:Fault/></env:Body></env:Envelope>";
        var r=run(new SoapHttpIntegrationV2("1.2","Operation",null,envelope,envelope,
                500,Map.of(),Map.of()),new HttpRestIntegrationV2(null,null));
        var soap=(SoapHttpIntegrationV2)r.messageRepresentations().getFirst().integration();
        assertEquals(envelope,soap.responseEnvelope());
        assertEquals(500,soap.httpStatusCode());
        assertEquals(7,r.totalDurationMs());
    }

    @Test void rejectsExternalEntitiesAndMalformedSoapWithoutExecutionSideEffects() {
        String dangerous="<!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]><x>&e;</x>";
        assertThrows(IllegalArgumentException.class,()->run(new SoapHttpIntegrationV2(
                "1.2",null,null,dangerous,null,null,null,null),
                new HttpRestIntegrationV2(null,null)));
        assertThrows(IllegalArgumentException.class,()->run(new SoapHttpIntegrationV2(
                "1.2",null,null,"<not-closed",null,null,null,null),
                new HttpRestIntegrationV2(null,null)));
    }

    @Test void rejectsOversizedRepresentation() {
        assertThrows(IllegalArgumentException.class,()->run(new HttpRestIntegrationV2(
                new HttpRequestV2(HttpMethodV2.GET,"/",null,"x".repeat(32769)),null),
                new HttpRestIntegrationV2(null,null)));
    }

    @Test void snapshotsDoNotChangeWhenOriginalHeadersAreMutated() {
        Map<String,String> headers=new HashMap<>();headers.put("Accept","application/json");
        var request=new HttpRequestV2(HttpMethodV2.GET,"/",headers,null);
        var r=run(new HttpRestIntegrationV2(request,null),new HttpRestIntegrationV2(null,null));
        headers.put("Accept","text/html");
        var snap=(HttpRestIntegrationV2)r.messageRepresentations().getFirst().integration();
        assertEquals("application/json",snap.request().headers().get("Accept"));
        assertThrows(UnsupportedOperationException.class,()->r.messageRepresentations().clear());
    }

    @Test void sqlTextIsNeverExecutedAndFollowsExecutedConnection() {
        var components=List.<ComponentV2>of(new ClientV2("c","C"),
                new ApiV2("a","A",1,1),new DatabaseV2("d","DB",1));
        var connections=List.of(new ConnectionV2("ca","c","a",1,new HttpRestIntegrationV2(null,null)),
                new ConnectionV2("ad","a","d",1,new DatabaseQueryIntegrationV2(
                        "DROP TABLE example","illustrative response")));
        var r=new SimulationEngine().execute(new ArchitectureV2(components,connections),
                new ScenarioV2("c","ca",List.of(new ApiExecutionV2("a","ad"))));
        assertEquals(2,r.messageRepresentations().size());
        var query=(DatabaseQueryIntegrationV2)r.messageRepresentations().get(1).integration();
        assertEquals("DROP TABLE example",query.queryText());
        assertEquals(7,r.totalDurationMs());
    }
}
