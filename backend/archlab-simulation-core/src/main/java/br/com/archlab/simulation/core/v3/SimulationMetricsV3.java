package br.com.archlab.simulation.core.v3;
import java.util.*;
public record SimulationMetricsV3(Long principalResponseDurationMs,Long principalTerminalDurationMs,Long globalCompletionDurationMs,long observationDurationMs,Long backgroundDurationAfterResponseMs,List<ComponentMetric> components,List<InteractionMetric> interactions,List<ResourceMetric> resources,List<GroupMetric> parallelGroups,List<CriticalPath> criticalPaths){
    public SimulationMetricsV3 {components=List.copyOf(components);interactions=List.copyOf(interactions);resources=List.copyOf(resources);parallelGroups=List.copyOf(parallelGroups);criticalPaths=List.copyOf(criticalPaths);}
    public record ComponentMetric(IdentifierV3 componentId,long activationCount,long completedActivationCount,long failedActivationCount,long totalProcessingWorkMs,long totalWaitingWorkMs,long totalCommunicationWorkMs){}
    public record InteractionMetric(IdentifierV3 interactionId,IdentifierV3 sourceComponentId,IdentifierV3 targetComponentId,long startedAtMs,Long terminalAtMs,Long durationMs,long attemptCount,long transportWorkMs,long remoteProcessingWorkMs,long waitingWorkMs){}
    public record ResourceMetric(IdentifierV3 resourceId,long acquiredCount,long rejectedCount,long timeoutCount,long totalWaitingMs,long totalBusyCapacityMs,long maxObservedInUse){}
    public record GroupMetric(IdentifierV3 parallelGroupId,long startedAtMs,Long joinSatisfiedAtMs,Long allBranchesTerminalAtMs){}
    public record CriticalPath(String target,Long durationMs,List<IdentifierV3> activityIds,String calculationVersion,String unavailableReason){public CriticalPath{activityIds=List.copyOf(activityIds);}}
}
