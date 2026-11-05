package br.com.archlab.simulation.core.v2;

public record CoapIntegrationV2(String method, String resource, String mode) implements IntegrationV2 {
    @Override public IntegrationKindV2 kind() { return IntegrationKindV2.COAP; }
}
