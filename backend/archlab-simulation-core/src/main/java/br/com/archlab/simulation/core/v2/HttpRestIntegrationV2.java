package br.com.archlab.simulation.core.v2;

/** Request/response are optional descriptive templates. */
public record HttpRestIntegrationV2(HttpRequestV2 request,
                                    HttpResponseV2 response) implements IntegrationV2 {
    @Override public IntegrationKindV2 kind() { return IntegrationKindV2.HTTP_REST; }
}
