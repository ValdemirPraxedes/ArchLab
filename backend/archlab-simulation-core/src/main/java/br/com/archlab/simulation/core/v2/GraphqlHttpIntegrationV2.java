package br.com.archlab.simulation.core.v2;

public record GraphqlHttpIntegrationV2(String operationType, String operationName) implements IntegrationV2 {
    @Override public IntegrationKindV2 kind() { return IntegrationKindV2.GRAPHQL_HTTP; }
}
