package br.com.archlab.simulation.core.v2;

import br.com.archlab.simulation.core.engine.SimulationEngine;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ApiProcessingOverrideV2Test {
    private static final IntegrationV2 HTTP = new HttpRestIntegrationV2(null, null);
    private ArchitectureV2 architecture(ApiProcessingOverrideV2 ab,ApiProcessingOverrideV2 cb) {
        return new ArchitectureV2(List.of(
                new ClientV2("x","Client X"),new ClientV2("y","Client Y"),
                new ApiV2("a","A",2,3),new ApiV2("c","C",2,3),
                new ApiV2("b","B",20,10),new DatabaseV2("db","DB",10)),
                List.of(new ConnectionV2("xa","x","a",1,HTTP),
                        new ConnectionV2("yc","y","c",1,HTTP),
                        new ConnectionV2("ab","a","b",1,HTTP,ab),
                        new ConnectionV2("cb","c","b",1,HTTP,cb),
                        new ConnectionV2("bd","b","db",1,new DatabaseQueryIntegrationV2(null,null))));
    }
    private NestedExecutionResultV2 execute(ArchitectureV2 arch,String first,String entry,String path,boolean db) {
        List<ApiExecutionV2> choices=new ArrayList<>(List.of(new ApiExecutionV2(first,path)));
        if(db) choices.add(new ApiExecutionV2("b","bd"));
        String client=first.equals("a")?"x":"y";
        return new SimulationEngine().execute(arch,new ScenarioV2(client,entry,choices));
    }
    private long processed(NestedExecutionResultV2 r,String component) {
        return r.metrics().processingByComponent().stream()
                .filter(m->m.componentId().equals(component)).findFirst().orElseThrow().durationMs();
    }
    @Test void defaultAndTwoIndependentIncomingOverridesResolveOnDestinationApi() {
        var arch=architecture(new ApiProcessingOverrideV2(40,15),new ApiProcessingOverrideV2(10,5));
        var byA=execute(arch,"a","xa","ab",false);
        var byC=execute(arch,"c","yc","cb",false);
        assertEquals(55,processed(byA,"b"));
        assertEquals(15,processed(byC,"b"));
        assertEquals(64,byA.totalDurationMs());
        assertEquals(24,byC.totalDurationMs());
        assertEquals(4,byA.metrics().communicationByConnection().stream().mapToLong(CommunicationMetricV2::durationMs).sum());
        assertTrue(byA.timeline().stream().filter(p->p instanceof ProcessingActivityV2)
                .map(p->(ProcessingActivityV2)p).filter(p->p.componentId().equals("b"))
                .allMatch(p->p.phase().startsWith("API_")));
        var baseline=execute(architecture(null,null),"a","xa","ab",false);
        assertEquals(30,processed(baseline,"b"));
        assertEquals(39,baseline.totalDurationMs());
    }
    @Test void zeroProcessingOverridesAreValid() {
        var r=execute(architecture(new ApiProcessingOverrideV2(0,0),null),"a","xa","ab",false);
        assertEquals(0,processed(r,"b"));
        assertEquals(9,r.totalDurationMs());
        assertEquals(2,r.timeline().stream().filter(a->a instanceof ProcessingActivityV2 p
                && p.componentId().equals("b") && p.durationMs()==0).count());
    }
    @Test void nestedWaitAndCorrelationsAreUnchanged() {
        var r=execute(architecture(new ApiProcessingOverrideV2(40,15),null),"a","xa","ab",true);
        assertEquals(76,r.totalDurationMs());
        assertEquals(55,processed(r,"b"));
        assertEquals(2,r.metrics().waitingByComponent().size());
        assertEquals(3,r.exchanges().size());
        assertEquals("exchange-1",r.exchanges().get(1).parentExchangeId());
        assertEquals("exchange-2",r.exchanges().get(2).parentExchangeId());
        assertEquals(r.totalDurationMs(),r.timeline().stream().mapToLong(TimelineActivityV2::durationMs).sum());
    }
    @Test void invalidDurationsAndDatabaseOverrideAreRejectedByDomain() {
        var bad=architecture(new ApiProcessingOverrideV2(-1,1),
                new ApiProcessingOverrideV2(1,1_000_000_001));
        var validation=new ArchitectureGraphValidatorV2().validate(bad);
        assertFalse(validation.valid());
        assertEquals(2,validation.violations().stream().filter(x->x.code().equals("INVALID_DURATION")).count());
        var conns=new ArrayList<>(architecture(null,null).connections());
        conns.set(4,new ConnectionV2("bd","b","db",1,new DatabaseQueryIntegrationV2(null,null),
                new ApiProcessingOverrideV2(1,2)));
        var db=new ArchitectureGraphValidatorV2().validate(
                new ArchitectureV2(architecture(null,null).components(),conns));
        assertTrue(db.violations().stream().anyMatch(x->x.path().endsWith(".apiProcessingOverride")));
    }
}
