package br.com.archlab.simulation.core.engine;

import br.com.archlab.simulation.core.domain.*;
import br.com.archlab.simulation.core.event.*;

public final class ClientBehavior implements ComponentBehavior {
    @Override public void handle(SimulationEvent event, SimulationContext context) {
        if (event instanceof RequestSent sent) {
            if (!sent.sourceId().equals(clientId(context))) throw new IllegalStateException("Unexpected request source");
            context.sendRequest(sent.sourceId(), sent.targetId());
        } else if (event instanceof ResponseArrived arrived) {
            if (!arrived.targetId().equals(clientId(context))) throw new IllegalStateException("Unexpected response destination");
            context.scheduleAfter(0, new SimulationCompleted(arrived.requestId()));
        } else {
            throw new IllegalStateException("Unexpected client event: " + event);
        }
    }

    private String clientId(SimulationContext context) {
        return context.architecture().components().stream().filter(c -> c.type() == ComponentType.CLIENT)
                .findFirst().orElseThrow().id();
    }
}
