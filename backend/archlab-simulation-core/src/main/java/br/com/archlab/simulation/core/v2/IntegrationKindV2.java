package br.com.archlab.simulation.core.v2;

public enum IntegrationKindV2 { HTTP_REST, SOAP_HTTP, DATABASE_QUERY, HTTP_GENERIC, GRAPHQL_HTTP,
    GRPC_UNARY, COAP, WEBSOCKET, MQTT, QUEUE, TOPIC;
    public boolean isMessaging() { return this == MQTT || this == QUEUE || this == TOPIC; }
}
