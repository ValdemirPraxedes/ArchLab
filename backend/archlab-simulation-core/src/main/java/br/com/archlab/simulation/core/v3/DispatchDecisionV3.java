package br.com.archlab.simulation.core.v3;
import java.util.Objects;
public sealed interface DispatchDecisionV3 {
    record Apply(EffectBatchV3 batch) implements DispatchDecisionV3 {public Apply {Objects.requireNonNull(batch);}}
    record Ignore(String reason) implements DispatchDecisionV3 {public Ignore {if(reason==null || reason.isEmpty())throw new IllegalArgumentException("Reason required");}}
}
