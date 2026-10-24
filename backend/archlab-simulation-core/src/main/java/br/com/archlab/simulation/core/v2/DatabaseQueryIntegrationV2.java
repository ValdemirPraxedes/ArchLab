package br.com.archlab.simulation.core.v2;

/** Query text and response text are never executed. */
public record DatabaseQueryIntegrationV2(String queryText,
                                         String responseText) implements IntegrationV2 {
    @Override public IntegrationKindV2 kind() { return IntegrationKindV2.DATABASE_QUERY; }
}
