package br.com.archlab.simulation.core.v4;

import br.com.archlab.simulation.core.v2.WebSocketIntegrationV2;
import br.com.archlab.simulation.core.v2.WebSocketMessageV2;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class WebSocketRuntimeV4Test {
    @Test void messagesAlternateDirectionsUnderVirtualClock() {
        var integration = new WebSocketIntegrationV2("/events", 5000, null, "AUTOMATIC", List.of(
                new WebSocketMessageV2("SOURCE", 0, "subscribe"),
                new WebSocketMessageV2("TARGET", 25, "event")));
        var result = new WebSocketRuntimeV4().run(integration, 10);
        assertEquals(ExecutionLifecycleV4.GlobalStatus.COMPLETED, result.status());
        assertEquals(55, result.totalDurationMs());
        assertEquals(10, result.messages().get(0).sentAtMs());
        assertEquals(20, result.messages().get(0).arrivedAtMs());
        assertEquals(45, result.messages().get(1).sentAtMs());
        assertEquals(55, result.messages().get(1).arrivedAtMs());
    }

    @Test void connectTimeoutPreventsApplicationMessages() {
        var integration = new WebSocketIntegrationV2("/events", 5, null, "AUTOMATIC",
                List.of(new WebSocketMessageV2("SOURCE", 0, "never")));
        var result = new WebSocketRuntimeV4().run(integration, 10);
        assertEquals(ExecutionLifecycleV4.MainStatus.TIMED_OUT, result.mainStatus());
        assertEquals(5, result.totalDurationMs());
        assertTrue(result.messages().isEmpty());
    }
}
