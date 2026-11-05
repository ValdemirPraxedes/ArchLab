package br.com.archlab.simulation.core.v2;

public record TopicIntegrationV2(String topic) implements IntegrationV2 {
    @Override public IntegrationKindV2 kind() { return IntegrationKindV2.TOPIC; }
}
