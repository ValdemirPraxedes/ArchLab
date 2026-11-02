package br.com.archlab.simulation.core.v3;
import java.util.*;
/** Public-snapshot delta; internal entities use the dispatch audit instead. */
public record StateDeltaV3(IdentifierV3 deltaId,long sequence,long timestampMs,IdentifierV3 sourceTraceRecordId,
                           RuntimeEntityV3.Kind entityKind,IdentifierV3 entityId,Operation operation,Long expectedVersion,
                           Long nextVersion,RuntimeEntityV3 before,RuntimeEntityV3 after) {
    public enum Operation {CREATE,UPDATE,REMOVE}
    public static boolean publicKind(RuntimeEntityV3.Kind kind){return Set.of(RuntimeEntityV3.Kind.ACTIVATION,RuntimeEntityV3.Kind.INTERACTION,RuntimeEntityV3.Kind.ATTEMPT,RuntimeEntityV3.Kind.PARALLEL_GROUP,RuntimeEntityV3.Kind.RESOURCE,RuntimeEntityV3.Kind.QUEUE_ENTRY,RuntimeEntityV3.Kind.PUBLICATION,RuntimeEntityV3.Kind.DELIVERY_ATTEMPT).contains(kind);}
    public StateDeltaV3 {Objects.requireNonNull(deltaId);Objects.requireNonNull(sourceTraceRecordId);Objects.requireNonNull(entityKind);Objects.requireNonNull(entityId);Objects.requireNonNull(operation);
        if(sequence<0 || timestampMs<0 || !publicKind(entityKind))throw new IllegalArgumentException("Invalid public delta");
        if(before!=null && (!before.id().equals(entityId) || before.kind()!=entityKind) || after!=null && (!after.id().equals(entityId) || after.kind()!=entityKind))throw new IllegalArgumentException("Delta identity mismatch");
        boolean valid=switch(operation){case CREATE->before==null && expectedVersion==null && after!=null && after.version()==0 && Objects.equals(nextVersion,0L);
            case UPDATE->before!=null && after!=null && Objects.equals(expectedVersion,before.version()) && Objects.equals(nextVersion,after.version()) && after.version()==Math.addExact(before.version(),1);
            case REMOVE->before!=null && after==null && Objects.equals(expectedVersion,before.version()) && nextVersion==null;};
        if(!valid)throw new IllegalArgumentException("Invalid delta versions/operation");
    }
}
