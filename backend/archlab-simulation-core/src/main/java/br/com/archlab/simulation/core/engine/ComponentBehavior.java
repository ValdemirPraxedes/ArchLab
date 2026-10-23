package br.com.archlab.simulation.core.engine;

import br.com.archlab.simulation.core.event.SimulationEvent;

public interface ComponentBehavior {
    void handle(SimulationEvent event, SimulationContext context);
}
