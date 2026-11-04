package br.com.archlab.simulation.core.v2;

public record QueueIntegrationV2(String queueName) implements IntegrationV2 {
    @Override public IntegrationKindV2 kind() { return IntegrationKindV2.QUEUE; }
}
