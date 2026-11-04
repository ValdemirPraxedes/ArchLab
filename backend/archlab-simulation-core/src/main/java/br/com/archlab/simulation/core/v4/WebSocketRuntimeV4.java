package br.com.archlab.simulation.core.v4;

import br.com.archlab.simulation.core.v2.WebSocketIntegrationV2;
import br.com.archlab.simulation.core.v2.WebSocketMessageV2;
import java.util.ArrayList;
import java.util.List;

/** Deterministic virtual session; no socket or wall-clock work is performed. */
public final class WebSocketRuntimeV4 {
    public record MessageSpan(int index, WebSocketMessageV2 message, long sentAtMs, long arrivedAtMs) {}
    public record Result(ExecutionLifecycleV4.MainStatus mainStatus,
                         ExecutionLifecycleV4.GlobalStatus status, long totalDurationMs,
                         long openedAtMs, List<MessageSpan> messages) {}

    public Result run(WebSocketIntegrationV2 integration, long connectionLatencyMs) {
        if (connectionLatencyMs < 0 || integration.connectTimeoutMs() < 0
                || integration.idleTimeoutMs() != null && integration.idleTimeoutMs() < 0
                || integration.messages().size() > 100)
            throw new IllegalArgumentException("Invalid WebSocket timing or message count");
        var lifecycle = new ExecutionLifecycleV4();
        lifecycle.open("session-1", ExecutionLifecycleV4.ResourceKind.WEBSOCKET_SESSION);
        lifecycle.eventAt(0);
        if (connectionLatencyMs > integration.connectTimeoutMs()) {
            lifecycle.eventAt(integration.connectTimeoutMs());
            lifecycle.finish("session-1", ExecutionLifecycleV4.ResourceStatus.TIMED_OUT);
            lifecycle.finishMain(ExecutionLifecycleV4.MainStatus.TIMED_OUT);
            return new Result(lifecycle.mainStatus(), lifecycle.globalStatus(), lifecycle.totalDurationMs(), -1, List.of());
        }
        lifecycle.eventAt(connectionLatencyMs);
        long time = connectionLatencyMs;
        List<MessageSpan> spans = new ArrayList<>();
        for (int i = 0; i < integration.messages().size(); i++) {
            WebSocketMessageV2 message = integration.messages().get(i);
            if (message.intervalMs() < 0) throw new IllegalArgumentException("Negative WebSocket interval");
            long sent = Math.addExact(time, message.intervalMs());
            long arrived = Math.addExact(sent, connectionLatencyMs);
            if (integration.idleTimeoutMs() != null && message.intervalMs() > integration.idleTimeoutMs()) {
                lifecycle.eventAt(Math.addExact(time, integration.idleTimeoutMs()));
                lifecycle.finish("session-1", ExecutionLifecycleV4.ResourceStatus.TIMED_OUT);
                lifecycle.finishMain(ExecutionLifecycleV4.MainStatus.TIMED_OUT);
                return new Result(lifecycle.mainStatus(), lifecycle.globalStatus(), lifecycle.totalDurationMs(),
                        connectionLatencyMs, List.copyOf(spans));
            }
            lifecycle.eventAt(sent);
            lifecycle.eventAt(arrived);
            spans.add(new MessageSpan(i + 1, message, sent, arrived));
            time = arrived;
        }
        lifecycle.finish("session-1", ExecutionLifecycleV4.ResourceStatus.COMPLETED);
        lifecycle.finishMain(ExecutionLifecycleV4.MainStatus.COMPLETED);
        return new Result(lifecycle.mainStatus(), lifecycle.globalStatus(), lifecycle.totalDurationMs(),
                connectionLatencyMs, List.copyOf(spans));
    }
}
