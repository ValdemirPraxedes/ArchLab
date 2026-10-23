package br.com.archlab.simulation.core.engine;

import br.com.archlab.simulation.core.domain.*;
import java.util.Map;
import java.util.Objects;

public final class BehaviorRegistry {
    private final Map<ComponentType, ComponentBehavior> behaviors;

    public BehaviorRegistry() {
        behaviors = Map.of(
                ComponentType.CLIENT, new ClientBehavior(),
                ComponentType.API, new ApiBehavior(),
                ComponentType.DATABASE, new DatabaseBehavior());
    }

    public ComponentBehavior behaviorFor(Component component) {
        return Objects.requireNonNull(behaviors.get(component.type()));
    }
}
