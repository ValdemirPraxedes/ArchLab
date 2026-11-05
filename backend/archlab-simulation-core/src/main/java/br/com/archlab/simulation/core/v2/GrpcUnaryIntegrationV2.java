package br.com.archlab.simulation.core.v2;

public record GrpcUnaryIntegrationV2(String service, String method) implements IntegrationV2 {
    @Override public IntegrationKindV2 kind() { return IntegrationKindV2.GRPC_UNARY; }
}
