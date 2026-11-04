package br.com.archlab.simulation.core.v2;

import java.util.List;

public record WebSocketIntegrationV2(String path, long connectTimeoutMs, Long idleTimeoutMs,
                                     String closeMode, List<WebSocketMessageV2> messages) implements IntegrationV2 {
    public WebSocketIntegrationV2 { messages = List.copyOf(messages); }
    @Override public IntegrationKindV2 kind() { return IntegrationKindV2.WEBSOCKET; }
}
