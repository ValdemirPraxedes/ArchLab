package br.com.archlab.simulation.core.v2;
import br.com.archlab.simulation.core.engine.SimulationTraceEntry;
import java.util.List;

/** Immutable event histories plus trace-derived v2 timeline and metrics. */
public record NestedExecutionResultV2(long totalDurationMs, String requestId,
        List<SimulationTraceEntry> events, List<ExchangeTraceV2> exchanges,
        List<CorrelatedMessageV2> messages, List<ComponentTransitionV2> componentTransitions,
        List<TimelineActivityV2> timeline, SimulationMetricsV2 metrics) {
    public NestedExecutionResultV2 {
        if (totalDurationMs < 0) throw new IllegalArgumentException("Negative duration");
        events=List.copyOf(events); exchanges=List.copyOf(exchanges);
        messages=List.copyOf(messages); componentTransitions=List.copyOf(componentTransitions);
        timeline=List.copyOf(timeline);
    }
    public NestedExecutionResultV2(long duration,String id,List<SimulationTraceEntry> events,
            List<ExchangeTraceV2> exchanges,List<CorrelatedMessageV2> messages,
            List<ComponentTransitionV2> transitions) {
        this(duration,id,events,exchanges,messages,transitions,
                new TimelineMetricsProjectorV2().project(duration,events,exchanges,messages,transitions));
    }
    private NestedExecutionResultV2(long duration,String id,List<SimulationTraceEntry> events,
            List<ExchangeTraceV2> exchanges,List<CorrelatedMessageV2> messages,
            List<ComponentTransitionV2> transitions,TimelineMetricsProjectorV2.Projection p) {
        this(duration,id,events,exchanges,messages,transitions,p.timeline(),p.metrics());
    }
    public NestedExecutionResultV2(long duration,String id,List<SimulationTraceEntry> events) {
        this(duration,id,events,List.of(),List.of(),List.of());
    }
}
