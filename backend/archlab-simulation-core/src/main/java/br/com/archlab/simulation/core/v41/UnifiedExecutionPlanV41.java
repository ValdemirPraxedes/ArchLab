package br.com.archlab.simulation.core.v41;

import br.com.archlab.simulation.core.v2.ArchitectureV2;
import br.com.archlab.simulation.core.v41.BehaviorPlanV41.Behavior;
import java.util.*;

/** Immutable execution input shared by the validator adapter and the pure Java runtime. */
public record UnifiedExecutionPlanV41(ArchitectureV2 architecture, Entry entry,
                                      List<SessionSetup> sessionSetup,
                                      Map<String,List<Behavior>> behaviors) {
    public record Entry(String componentId,String connectionId,String operation) {}
    public record SessionSetup(String connectionId,String alias,boolean autoClose) {}

    public UnifiedExecutionPlanV41 {
        Objects.requireNonNull(architecture);
        Objects.requireNonNull(entry);
        sessionSetup=List.copyOf(sessionSetup);
        Map<String,List<Behavior>> copy=new LinkedHashMap<>();
        behaviors.forEach((id,plans) -> copy.put(id,List.copyOf(plans)));
        behaviors=Collections.unmodifiableMap(copy);
    }

    public Behavior match(String componentId,String trigger,String operation,String destination,String path) {
        for (Behavior behavior:behaviors.getOrDefault(componentId,List.of()))
            if (behavior.trigger().equals(trigger) && (behavior.operation()==null || behavior.operation().equals(operation))
                    && (behavior.destinationName()==null || behavior.destinationName().equals(destination))
                    && (behavior.websocketPath()==null || behavior.websocketPath().equals(path))) return behavior;
        return null;
    }
}
