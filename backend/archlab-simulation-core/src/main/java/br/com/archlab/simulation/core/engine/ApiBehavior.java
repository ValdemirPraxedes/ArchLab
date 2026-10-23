package br.com.archlab.simulation.core.engine;

import br.com.archlab.simulation.core.domain.*;
import br.com.archlab.simulation.core.event.*;

public final class ApiBehavior implements ComponentBehavior {
    @Override public void handle(SimulationEvent event, SimulationContext context) {
        ApiComponent api = (ApiComponent) context.architecture().components().stream()
                .filter(c -> c instanceof ApiComponent).findFirst().orElseThrow();
        if (event instanceof RequestArrived arrived) {
            if (!api.id().equals(arrived.targetId())) throw new IllegalStateException("Unexpected API destination");
            context.scheduleAfter(0, new ProcessingStarted(arrived.requestId(), api.id()));
        } else if (event instanceof ProcessingStarted started) {
            if (!api.id().equals(started.componentId())) throw new IllegalStateException("Unexpected API processing");
            context.scheduleAfter(api.preProcessingMs(), new ProcessingCompleted(started.requestId(), api.id()));
        } else if (event instanceof ProcessingCompleted completed) {
            if (!api.id().equals(completed.componentId())) throw new IllegalStateException("Unexpected API processing");
            String dbId = context.architecture().components().stream().filter(c -> c.type() == ComponentType.DATABASE)
                    .findFirst().orElseThrow().id();
            context.scheduleAfter(0, new RequestSent(completed.requestId(), api.id(), dbId));
        } else if (event instanceof ResponseArrived arrived) {
            if (!api.id().equals(arrived.targetId())) throw new IllegalStateException("Unexpected database response");
            context.scheduleAfter(api.postProcessingMs(), new ResponseSent(arrived.requestId(), api.id(),
                    context.architecture().components().stream().filter(c -> c.type() == ComponentType.CLIENT)
                            .findFirst().orElseThrow().id()));
        } else if (event instanceof RequestSent sent) {
            context.sendRequest(sent.sourceId(), sent.targetId());
        } else if (event instanceof ResponseSent sent) {
            context.sendResponse(sent.sourceId(), sent.targetId());
        } else {
            throw new IllegalStateException("Unexpected API event: " + event);
        }
    }
}
