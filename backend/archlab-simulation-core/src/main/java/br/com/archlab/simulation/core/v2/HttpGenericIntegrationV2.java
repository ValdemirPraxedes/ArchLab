package br.com.archlab.simulation.core.v2;

public record HttpGenericIntegrationV2(String method, String path) implements IntegrationV2 {
    @Override public IntegrationKindV2 kind() { return IntegrationKindV2.HTTP_GENERIC; }
}
