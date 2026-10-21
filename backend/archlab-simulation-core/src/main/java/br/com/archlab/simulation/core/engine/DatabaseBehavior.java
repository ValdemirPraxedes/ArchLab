package br.com.archlab.simulation.core.engine;

import br.com.archlab.simulation.core.domain.*;
import br.com.archlab.simulation.core.event.*;

public final class DatabaseBehavior implements ComponentBehavior {
    @Override public void handle(SimulationEvent event, SimulationContext context) {
        DatabaseComponent db = (DatabaseComponent) context.architecture().components().stream()
                .filter(c -> c instanceof DatabaseComponent).findFirst().orElseThrow();
        if (event instanceof RequestArrived arrived) {
            if (!db.id().equals(arrived.targetId())) throw new IllegalStateException("Unexpected database destination");
            context.scheduleAfter(0, new ProcessingStarted(arrived.requestId(), db.id()));
        } else if (event instanceof ProcessingStarted started) {
            if (!db.id().equals(started.componentId())) throw new IllegalStateException("Unexpected database processing");
            context.scheduleAfter(db.processingMs(), new ProcessingCompleted(started.requestId(), db.id()));
        } else if (event instanceof ProcessingCompleted completed) {
            if (!db.id().equals(completed.componentId())) throw new IllegalStateException("Unexpected database processing");
            String apiId = context.architecture().components().stream().filter(c -> c.type() == ComponentType.API)
                    .findFirst().orElseThrow().id();
            context.scheduleAfter(0, new ResponseSent(completed.requestId(), db.id(), apiId));
        } else if (event instanceof ResponseSent sent) {
            context.sendResponse(sent.sourceId(), sent.targetId());
        } else {
            throw new IllegalStateException("Unexpected database event: " + event);
        }
    }
}
