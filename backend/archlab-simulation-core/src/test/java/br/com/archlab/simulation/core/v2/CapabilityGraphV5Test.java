package br.com.archlab.simulation.core.v2;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class CapabilityGraphV5Test {
    private static ServiceComponentV5 component(String id,ComponentTypeV2 type) {
        return new ServiceComponentV5(id,id,type,0,0,0,List.of(),List.of(),List.of(),null);
    }
    @Test void workerCannotReceiveSynchronousClientRequest() {
        var architecture=new ArchitectureV2(List.of(new ClientV2("client","Client"),
                component("worker",ComponentTypeV2.WORKER)),List.of(
                new ConnectionV2("edge","client","worker",1,new HttpRestIntegrationV2(null,null),null)));
        var result=new ArchitectureGraphValidatorV2().validate(architecture);
        assertFalse(result.valid());
        assertTrue(result.violations().stream().anyMatch(v -> v.code().equals("INVALID_CONNECTION")));
    }
    @Test void virtualCacheRejectsWebSocketProfile() {
        var architecture=new ArchitectureV2(List.of(component("service",ComponentTypeV2.SERVICE),
                component("cache",ComponentTypeV2.CACHE)),List.of(
                new ConnectionV2("edge","service","cache",1,
                        new WebSocketIntegrationV2("/cache",5L,null,"AUTOMATIC",List.of()),null)));
        var result=new ArchitectureGraphValidatorV2().validate(architecture);
        assertFalse(result.valid());
        assertTrue(result.violations().stream().anyMatch(v -> v.code().equals("INVALID_INTEGRATION")));
    }
}
