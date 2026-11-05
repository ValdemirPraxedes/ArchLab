package br.com.archlab.simulation.core.v41;

import br.com.archlab.simulation.core.v2.*;
import br.com.archlab.simulation.core.v41.BehaviorPlanV41.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SchedulerV5Test {
    @Test void occurrencesUseTheSharedVirtualClockAndRemainFinite() {
        var scheduler=new ServiceComponentV5("scheduler","Scheduler",ComponentTypeV2.SCHEDULER,
                0,0,0,List.of(new ServiceComponentV5.Schedule("hourly",5,10,3,"tick")),
                List.of(),List.of(),null);
        var edge=new ConnectionV2("in","client","scheduler",1,new HttpRestIntegrationV2(null,null),null);
        var root=new Behavior("root","REQUEST_RECEIVED",null,null,null,List.of(
                new Action("schedule","SCHEDULE_TRIGGER",List.of(),null,null,null,null,null,
                        0,0,200,List.of(),Map.of("scheduleId","hourly")),
                new Action("respond","RESPOND",List.of("schedule"),null,null,null,null,null,
                        0,0,200,List.of())));
        var tick=new Behavior("tick","SCHEDULED_TRIGGER",null,null,null,List.of(
                new Action("work","PROCESS",List.of(),null,null,null,null,null,
                        1,0,200,List.of())));
        var plan=new UnifiedExecutionPlanV41(
                new ArchitectureV2(List.of(new ClientV2("client","Client"),scheduler),List.of(edge)),
                new UnifiedExecutionPlanV41.Entry("client","in",null),List.of(),
                Map.of("scheduler",List.of(root,tick)));
        var result=new UnifiedSimulationV41(plan).run();
        @SuppressWarnings("unchecked") List<Map<String,Object>> trace=(List<Map<String,Object>>)result.get("trace");
        assertEquals(3,trace.stream().filter(row -> "SCHEDULED_TRIGGER".equals(row.get("type"))).count());
        assertEquals(27L,((Number)((Map<?,?>)result.get("execution")).get("totalDurationMs")).longValue());
        assertEquals(result,new UnifiedSimulationV41(plan).run());
    }
}
