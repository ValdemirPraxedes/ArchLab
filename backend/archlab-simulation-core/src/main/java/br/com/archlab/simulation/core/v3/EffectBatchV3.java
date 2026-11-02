package br.com.archlab.simulation.core.v3;
import java.util.*;
/** Ordered effects contain only immutable domain values, never executable callbacks. */
public record EffectBatchV3(List<Guard> guards,List<Effect> effects) {
    public EffectBatchV3 {guards=List.copyOf(guards);effects=List.copyOf(effects);}
    public static EffectBatchV3 of(Effect... effects){return new EffectBatchV3(List.of(),List.of(effects));}
    public sealed interface Guard { }
    public record EntityVersion(IdentifierV3 id,long expectedVersion) implements Guard {public EntityVersion {Objects.requireNonNull(id);if(expectedVersion<0)throw new IllegalArgumentException("Negative version");}}
    public record EntityState(IdentifierV3 id,String expectedState) implements Guard {public EntityState {Objects.requireNonNull(id);Objects.requireNonNull(expectedState);}}
    public record ClaimAbsent(IdentifierV3 key) implements Guard {public ClaimAbsent {Objects.requireNonNull(key);}}
    public record Claim(IdentifierV3 key,IdentifierV3 owner,String purpose) {public Claim {Objects.requireNonNull(key);Objects.requireNonNull(owner);if(purpose==null || purpose.isEmpty())throw new IllegalArgumentException("Claim purpose required");}}
    public sealed interface Effect { }
    public record Create(RuntimeEntityV3 entity) implements Effect {public Create {Objects.requireNonNull(entity);}}
    public record Replace(long expectedVersion,RuntimeEntityV3 entity) implements Effect {public Replace {Objects.requireNonNull(entity);if(expectedVersion<0)throw new IllegalArgumentException("Negative version");}}
    public record Transition(IdentifierV3 id,long expectedVersion,String target) implements Effect {public Transition {Objects.requireNonNull(id);Objects.requireNonNull(target);if(expectedVersion<0)throw new IllegalArgumentException("Negative version");}}
    public record Remove(IdentifierV3 id,long expectedVersion) implements Effect {public Remove {Objects.requireNonNull(id);if(expectedVersion<0)throw new IllegalArgumentException("Negative version");}}
    public record Schedule(long atMs,DeterministicSchedulerV3.Phase phase,SimulationEventV3 payload) implements Effect {public Schedule {Objects.requireNonNull(phase);Objects.requireNonNull(payload);if(atMs<0)throw new IllegalArgumentException("Negative time");}}
    public record TakeClaim(Claim claim) implements Effect {public TakeClaim {Objects.requireNonNull(claim);}}
    public record AdvanceRandom(long expectedState,long nextState) implements Effect { }
    public record Emit(TraceRecordV3.Kind kind,TraceRecordV3.Context context,TraceRecordV3.Detail detail) implements Effect {public Emit {Objects.requireNonNull(kind);Objects.requireNonNull(detail);}}
}
