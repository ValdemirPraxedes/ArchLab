package br.com.archlab.simulation.core.v3;
import java.util.*;import static br.com.archlab.simulation.core.v3.RuntimeEntityV3.*;
/** Verifies the exact before value and source fact, not merely a matching version number. */
public final class PublicReplayV3 {
    private PublicReplayV3(){}
    public static PublicSnapshotV3 replay(PublicSnapshotV3 initial,List<StateDeltaV3> deltas,List<TraceRecordV3> trace,long observedUntil){
        if(observedUntil<initial.virtualTimeMs())throw new IllegalArgumentException("Observation before snapshot");var facts=new HashMap<IdentifierV3,TraceRecordV3>();long sequence=-1,time=initial.virtualTimeMs();IdentifierV3 execution=null;
        for(var fact:trace){if(fact.sequence()<=sequence || fact.timestampMs()<time || fact.timestampMs()>observedUntil || facts.put(fact.recordId(),fact)!=null || execution!=null && !execution.equals(fact.executionId()))throw new IllegalArgumentException("Invalid trace ordering/identity");sequence=fact.sequence();time=fact.timestampMs();execution=fact.executionId();}
        var state=new LinkedHashMap<>(initial.entities());var used=new HashSet<>(state.keySet());sequence=-1;time=initial.virtualTimeMs();
        var deltaIds=new HashSet<IdentifierV3>();for(var delta:deltas){if(!deltaIds.add(delta.deltaId()))throw new IllegalArgumentException("Duplicate delta ID");var fact=facts.get(delta.sourceTraceRecordId());if(delta.sequence()<=sequence || delta.timestampMs()<time || delta.timestampMs()>observedUntil || fact==null || fact.timestampMs()!=delta.timestampMs())throw new IllegalArgumentException("Invalid delta position/source");sequence=delta.sequence();time=delta.timestampMs();var current=state.get(delta.entityId());
            switch(delta.operation()){
                case CREATE -> {if(current!=null || !used.add(delta.entityId()))throw new IllegalArgumentException("Reused entity ID");state.put(delta.entityId(),delta.after());}
                case UPDATE -> {if(!Objects.equals(current,delta.before()))throw new IllegalArgumentException("Delta before mismatch");state.put(delta.entityId(),delta.after());}
                case REMOVE -> {if(!Objects.equals(current,delta.before()))throw new IllegalArgumentException("Delta before mismatch");state.remove(delta.entityId());}
            }
        }
        for(var value:state.values())switch(value){case Attempt a->{if(!(state.get(a.interactionId()) instanceof Interaction))throw new IllegalArgumentException("Missing interaction");}case Interaction i->{for(var id:i.attemptIds())if(!(state.get(id) instanceof Attempt a) || !a.interactionId().equals(i.id()))throw new IllegalArgumentException("Invalid attempt binding");}case DeliveryAttempt d->{if(!(state.get(d.queueEntryId()) instanceof QueueEntry))throw new IllegalArgumentException("Missing queue entry");}case QueueEntry q->{if(!(state.get(q.queueId()) instanceof Resource))throw new IllegalArgumentException("Missing queue resource");}default->{}}
        return PublicSnapshotV3.from(new RuntimeStateV3(state),observedUntil);
    }
}
