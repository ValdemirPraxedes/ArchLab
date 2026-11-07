package br.com.archlab.simulation.core.v41;

import br.com.archlab.simulation.core.v2.ComponentTypeV2;
import br.com.archlab.simulation.core.v2.DataResourceV6;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class VirtualDataResourcesV6LimitTest {
    private static final DataResourceV6 RESOURCE=new DataResourceV6("db","Data",
            ComponentTypeV2.RELATIONAL_DATABASE,0,512,0,0,1,1,1);

    private static BehaviorPlanV41.Action bulk(int count) {
        return new BehaviorPlanV41.Action("bulk","DB_BULK_INSERT",List.of(),"data",null,
                null,null,null,0,0,0,List.of(),Map.of("recordCount",count));
    }

    @Test void millionLogicalRecordsFitInBoundedStateAndNextBatchIsRejected() {
        var state=new VirtualDataResourcesV6();
        for (int i=0;i<10;i++) {
            var outcome=state.execute(RESOURCE,bulk(100_000));
            assertEquals("INSERTED",outcome.code());
            assertEquals(100_000,outcome.writtenCount());
        }
        assertEquals(1_000_000,state.count(RESOURCE));
        var rejected=state.execute(RESOURCE,bulk(1));
        assertEquals("LIMIT_EXCEEDED",rejected.code());
        assertEquals(0,rejected.writtenCount());
        assertEquals(1_000_000,state.count(RESOURCE));
        assertEquals(0,new VirtualDataResourcesV6().count(RESOURCE));
    }
    @Test void logicalByteBudgetRejectsOverflowAtomically() {
        var large=new DataResourceV6("large","Large",ComponentTypeV2.RELATIONAL_DATABASE,
                0,1_048_576,0,0,1,1,1);
        var state=new VirtualDataResourcesV6();
        assertEquals("INSERTED",state.execute(large,bulk(1_024)).code());
        assertEquals(1_024,state.count(large));
        var rejected=state.execute(large,bulk(1));
        assertEquals("LIMIT_EXCEEDED",rejected.code());
        assertEquals(1_024,state.count(large));
    }
}
