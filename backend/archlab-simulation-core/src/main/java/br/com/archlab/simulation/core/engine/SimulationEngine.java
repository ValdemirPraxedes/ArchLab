package br.com.archlab.simulation.core.engine;

import br.com.archlab.simulation.core.domain.*;
import br.com.archlab.simulation.core.event.*;
import br.com.archlab.simulation.core.validation.ArchitectureValidator;
import br.com.archlab.simulation.core.v2.ArchitectureV2;
import br.com.archlab.simulation.core.v2.ScenarioV2;
import br.com.archlab.simulation.core.v2.NestedCallRuntimeV2;
import br.com.archlab.simulation.core.v2.NestedExecutionResultV2;
import java.util.Objects;

/** Discrete-event driver. No threads, HTTP calls or wall-clock delays. */
public final class SimulationEngine {
    private static final long MAX_EVENTS = 100_000L;
    private final ArchitectureValidator validator = new ArchitectureValidator();
    private final BehaviorRegistry behaviors = new BehaviorRegistry();

    /** V2 uses the same deterministic DES primitives; hierarchical states follow in BE-06. */
    public NestedExecutionResultV2 execute(ArchitectureV2 architecture, ScenarioV2 scenario) {
        return new NestedCallRuntimeV2().run(architecture, scenario);
    }

    public SimulationResult execute(Architecture architecture) {
        validator.requireValid(architecture);
        SimulationContext context = new SimulationContext(architecture);
        String clientId = architecture.components().stream().filter(c -> c.type() == ComponentType.CLIENT)
                .findFirst().orElseThrow().id();
        String apiId = architecture.components().stream().filter(c -> c.type() == ComponentType.API)
                .findFirst().orElseThrow().id();
        String requestId = "request-1";
        context.start(requestId);
        context.scheduleAfter(0, new RequestSent(requestId, clientId, apiId));
        DiscreteEventLoop.run(context::hasEvents, context::next, scheduled -> {
            if (!requestId.equals(requestIdOf(scheduled.event()))) {
                throw new IllegalStateException("Unrelated request in execution");
            }
            if (scheduled.event() instanceof RequestArrived || scheduled.event() instanceof ResponseArrived) {
                context.onArrival(scheduled.event());
            }
            context.record(scheduled);
            if (scheduled.event() instanceof SimulationCompleted terminal) {
                context.complete(terminal.requestId());
                if (context.hasEvents()) throw new IllegalStateException("Events remain after completion");
                return;
            }
            Component target = context.component(componentId(scheduled.event()));
            behaviors.behaviorFor(target).handle(scheduled.event(), context);
        }, MAX_EVENTS);
        if (!context.completed()) throw new IllegalStateException("Event queue exhausted before terminal event");
        return context.result();
    }

    private static String requestIdOf(SimulationEvent event) {
        return switch (event) {
            case RequestSent e -> e.requestId();
            case RequestArrived e -> e.requestId();
            case ResponseSent e -> e.requestId();
            case ResponseArrived e -> e.requestId();
            case ProcessingStarted e -> e.requestId();
            case ProcessingCompleted e -> e.requestId();
            case SimulationCompleted e -> e.requestId();
        };
    }

    private static String componentId(SimulationEvent event) {
        return switch (event) {
            case RequestSent e -> e.sourceId();
            case RequestArrived e -> e.targetId();
            case ResponseSent e -> e.sourceId();
            case ResponseArrived e -> e.targetId();
            case ProcessingStarted e -> e.componentId();
            case ProcessingCompleted e -> e.componentId();
            case SimulationCompleted e -> throw new IllegalArgumentException("Terminal event is handled by the engine");
        };
    }
}
