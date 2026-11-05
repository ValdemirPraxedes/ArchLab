package br.com.archlab.simulation.core.v2;

public record MqttIntegrationV2(String topic, int qos) implements IntegrationV2 {
    @Override public IntegrationKindV2 kind() { return IntegrationKindV2.MQTT; }
}
