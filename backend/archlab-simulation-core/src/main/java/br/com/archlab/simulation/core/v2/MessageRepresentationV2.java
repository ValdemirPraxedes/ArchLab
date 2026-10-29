package br.com.archlab.simulation.core.v2;
import java.util.Objects;

/** Passive, immutable snapshot for a connection used in this simulation. */
public record MessageRepresentationV2(String connectionId, IntegrationV2 integration) {
    public MessageRepresentationV2 {
        Objects.requireNonNull(connectionId, "connectionId");
        Objects.requireNonNull(integration, "integration");
    }
}
