package br.com.archlab.simulation.core.v3;
import java.util.*;
import static br.com.archlab.simulation.core.v3.RuntimeEntityV3.*;
import br.com.archlab.simulation.core.v3.RuntimeEntityV3.Timer;
/** Persistent value state. Creating candidate values never mutates a published state. */
public record RuntimeStateV3(Map<IdentifierV3,RuntimeEntityV3> entities) {
    public RuntimeStateV3 {var sorted=new TreeMap<IdentifierV3,RuntimeEntityV3>(Comparator.comparing(IdentifierV3::value));
        for(var entry:entities.entrySet()){var e=Objects.requireNonNull(entry.getValue());if(!entry.getKey().equals(e.id()) || !StateTransitionsV3.known(e.kind(),e.stateName()))throw new IllegalArgumentException("Invalid entity identity or state");sorted.put(entry.getKey(),e);}entities=Collections.unmodifiableMap(sorted);}
    public static RuntimeStateV3 empty(){return new RuntimeStateV3(Map.of());}
    public RuntimeStateV3 create(RuntimeEntityV3 entity){if(entity.version()!=0 || entities.containsKey(entity.id()))throw new IllegalStateException("Entity already exists or nonzero creation version");var next=new LinkedHashMap<>(entities);next.put(entity.id(),entity);return new RuntimeStateV3(next);}
    public RuntimeStateV3 replace(long expectedVersion,RuntimeEntityV3 entity){var previous=entities.get(entity.id());
        if(previous==null || previous.kind()!=entity.kind() || previous.version()!=expectedVersion || entity.version()!=Math.addExact(expectedVersion,1))throw new IllegalStateException("Stale or incompatible replacement");
        if(!Objects.equals(previous.stateName(),entity.stateName()))StateTransitionsV3.require(previous.kind(),previous.stateName(),entity.stateName());
        var next=new LinkedHashMap<>(entities);next.put(entity.id(),entity);return new RuntimeStateV3(next);}
    public RuntimeStateV3 transition(IdentifierV3 id,long expectedVersion,String target){var entity=entities.get(id);if(entity==null)throw new IllegalStateException("Missing entity");return replace(expectedVersion,entity.transition(target));}
    public RuntimeStateV3 remove(IdentifierV3 id,long expectedVersion){var entity=entities.get(id);if(entity==null || entity.version()!=expectedVersion)throw new IllegalStateException("Stale removal");var next=new LinkedHashMap<>(entities);next.remove(id);return new RuntimeStateV3(next);}
    public List<String> invariantViolations(){var errors=new ArrayList<String>();
        for(var entity:entities.values())switch(entity){
            case Activation a -> expect(errors,a.componentId(),Kind.COMPONENT);
            case Interaction i -> {if(new HashSet<>(i.attemptIds()).size()!=i.attemptIds().size())errors.add("DUPLICATE_ATTEMPT:"+i.id().value());for(var id:i.attemptIds()){expect(errors,id,Kind.ATTEMPT);if(entities.get(id) instanceof Attempt attempt && !attempt.interactionId().equals(i.id()))errors.add("ATTEMPT_INTERACTION:"+id.value());}}
            case Attempt a -> expect(errors,a.interactionId(),Kind.INTERACTION);
            case ParallelGroup group -> {for(var branch:group.branchIds())if(entities.values().stream().noneMatch(x->x instanceof BranchCursor c && c.branchExecutionId().equals(branch) && c.parallelGroupId().equals(group.id())))errors.add("BRANCH_MISSING:"+branch.value());}
            case QueueEntry e -> expect(errors,e.queueId(),Kind.RESOURCE);
            case DeliveryAttempt d -> expect(errors,d.queueEntryId(),Kind.QUEUE_ENTRY);
            case Cursor c -> expect(errors,c.activationId(),Kind.ACTIVATION);
            case BranchCursor b -> expect(errors,b.parallelGroupId(),Kind.PARALLEL_GROUP);
            case Timer t -> expect(errors,t.ownerId(),null);
            case Lease lease -> {expect(errors,lease.resourceId(),Kind.RESOURCE);expect(errors,lease.claimId(),Kind.WAIT_CLAIM);
                if(entities.get(lease.claimId()) instanceof WaitClaim claim && !claim.resourceId().equals(lease.resourceId()))errors.add("CLAIM_RESOURCE:"+lease.id().value());}
            case WaitClaim claim -> {expect(errors,claim.resourceId(),Kind.RESOURCE);expect(errors,claim.ownerId(),null);
                long held=entities.values().stream().filter(x->x instanceof Lease l && l.claimId().equals(claim.id()) && l.status()==LeaseStatus.HELD).count();
                long total=entities.values().stream().filter(x->x instanceof Lease l && l.claimId().equals(claim.id())).count();
                if(total>1 || held>1 || held==1 && claim.status()!=WaitClaimStatus.ACQUIRED || claim.status()==WaitClaimStatus.ACQUIRED && total!=1)errors.add("CLAIM_LEASE:"+claim.id().value());}
            case WorkItem work -> expect(errors,work.ownerId(),null);
            case Resource resource -> {if(resource.inUse()!=null){long units=0;
                for(var x:entities.values())if(x instanceof Lease l && l.resourceId().equals(resource.id()) && l.status()==LeaseStatus.HELD)units=Math.addExact(units,l.units());
                if(units!=resource.inUse())errors.add("RESOURCE_USAGE:"+resource.id().value());}}
            default -> { }
        }
        return List.copyOf(errors);
    }
    public void requireConsistent(){var errors=invariantViolations();if(!errors.isEmpty())throw new IllegalStateException(errors.toString());}
    private void expect(List<String> errors,IdentifierV3 id,Kind kind){var entity=entities.get(id);if(entity==null || kind!=null && entity.kind()!=kind)errors.add("REFERENCE:"+id.value()+":"+kind);}
}
