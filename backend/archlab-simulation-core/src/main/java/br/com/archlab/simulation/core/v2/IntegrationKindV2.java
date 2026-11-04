package br.com.archlab.simulation.core.v2;

public enum IntegrationKindV2 { HTTP_REST, SOAP_HTTP, DATABASE_QUERY, MQTT, QUEUE, TOPIC;
    public boolean isMessaging() { return this == MQTT || this == QUEUE || this == TOPIC; }
}
