package br.com.archlab.simulation.core.v3;
import java.util.*;
import static br.com.archlab.simulation.core.v3.RuntimeEntityV3.Kind;
/** Closed native lifecycle tables. Policies need their own declared transition table. */
public final class StateTransitionsV3 {
    private StateTransitionsV3(){}
    private static final Map<Kind,Map<String,Set<String>>> TABLES=build();
    private static Map<Kind,Map<String,Set<String>>> build(){var all=new EnumMap<Kind,Map<String,Set<String>>>(Kind.class);
        var activation=table("READY:RUNNING,FAILED,CANCELLED","RUNNING:WAITING,COMPLETED,FAILED,CANCELLED","WAITING:RUNNING,COMPLETED,FAILED,CANCELLED","COMPLETED:","FAILED:","CANCELLED:");
        for(var k:List.of(Kind.ACTIVATION,Kind.CURSOR,Kind.BRANCH_CURSOR,Kind.STEP_EXECUTION))all.put(k,activation);
        all.put(Kind.INTERACTION,table("CREATED:IN_FLIGHT,FAILED,TIMED_OUT,CANCELLED","IN_FLIGHT:AWAITING_RESULT,COMPLETED,FAILED,TIMED_OUT,CANCELLED","AWAITING_RESULT:COMPLETED,FAILED,TIMED_OUT,CANCELLED","COMPLETED:","FAILED:","TIMED_OUT:","CANCELLED:"));
        all.put(Kind.ATTEMPT,table("SCHEDULED:IN_FLIGHT,FAILED,TIMED_OUT,CANCELLED","IN_FLIGHT:AWAITING_RESULT,COMPLETED,FAILED,TIMED_OUT,CANCELLED","AWAITING_RESULT:COMPLETED,FAILED,TIMED_OUT,CANCELLED","COMPLETED:","FAILED:","TIMED_OUT:","CANCELLED:"));
        all.put(Kind.PARALLEL_GROUP,table("CREATED:RUNNING,CANCELLED","RUNNING:JOIN_SATISFIED,JOIN_UNSATISFIABLE,CANCELLED","JOIN_SATISFIED:COMPLETED,CANCELLED","JOIN_UNSATISFIABLE:COMPLETED,CANCELLED","COMPLETED:","CANCELLED:"));
        all.put(Kind.QUEUE_ENTRY,table("READY:IN_DELIVERY,EXPIRED,DEAD_LETTERED,DISCARDED","IN_DELIVERY:READY,ACKNOWLEDGED,EXPIRED,DEAD_LETTERED,DISCARDED","ACKNOWLEDGED:","EXPIRED:","DEAD_LETTERED:","DISCARDED:"));
        all.put(Kind.PUBLICATION,table("CREATED:IN_TRANSIT,REJECTED,FAILED,TIMED_OUT","IN_TRANSIT:ACCEPTED,REJECTED,FAILED,TIMED_OUT","ACCEPTED:","REJECTED:","FAILED:","TIMED_OUT:"));
        all.put(Kind.DELIVERY_ATTEMPT,table("SCHEDULED:IN_TRANSIT,FAILED,TIMED_OUT,CANCELLED","IN_TRANSIT:DELIVERED,REJECTED,FAILED,TIMED_OUT,CANCELLED","DELIVERED:AWAITING_ACK,ACKNOWLEDGED,REJECTED,TIMED_OUT,FAILED,CANCELLED","AWAITING_ACK:ACKNOWLEDGED,REJECTED,TIMED_OUT,FAILED,CANCELLED","ACKNOWLEDGED:","REJECTED:","TIMED_OUT:","FAILED:","CANCELLED:"));
        all.put(Kind.TIMER,table("SCHEDULED:CONSUMED,CANCELLED","CONSUMED:","CANCELLED:"));all.put(Kind.LEASE,table("HELD:RELEASED","RELEASED:"));
        all.put(Kind.WAIT_CLAIM,table("WAITING:ACQUIRED,TIMED_OUT,REJECTED","ACQUIRED:","TIMED_OUT:","REJECTED:"));
        all.put(Kind.WORK_ITEM,table("PENDING:RESOLVED","RESOLVED:"));all.put(Kind.COMPONENT,table("AVAILABLE:UNAVAILABLE","UNAVAILABLE:AVAILABLE"));
        all.put(Kind.PRINCIPAL,table("IN_PROGRESS:RESPONDED,FAILED,TIMED_OUT,CANCELLED","RESPONDED:","FAILED:","TIMED_OUT:","CANCELLED:"));
        all.put(Kind.RESOURCE,table("AVAILABLE:UNAVAILABLE,EXHAUSTED","EXHAUSTED:AVAILABLE,UNAVAILABLE","UNAVAILABLE:AVAILABLE,EXHAUSTED"));return Map.copyOf(all);
    }
    private static Map<String,Set<String>> table(String... rows){var result=new LinkedHashMap<String,Set<String>>();for(var row:rows){var parts=row.split(":",-1);result.put(parts[0],parts[1].isEmpty()?Set.of():Set.of(parts[1].split(",")));}return Map.copyOf(result);}
    public static boolean known(Kind kind,String status){if(status==null)return !TABLES.containsKey(kind);if(kind==Kind.POLICY_STATE)return !status.isEmpty();return TABLES.getOrDefault(kind,Map.of()).containsKey(status);}
    public static boolean allowed(Kind kind,String from,String to){return TABLES.getOrDefault(kind,Map.of()).getOrDefault(from,Set.of()).contains(to);}
    public static boolean terminal(Kind kind,String status){var table=TABLES.get(kind);return table!=null && table.containsKey(status) && table.get(status).isEmpty();}
    public static void require(Kind kind,String from,String to){if(to==null || !allowed(kind,from,to))throw new IllegalStateException("Illegal "+kind+" transition: "+from+" -> "+to);}
}
