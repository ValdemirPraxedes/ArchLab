package br.com.archlab.simulation.core.v4;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExecutionLifecycleV4Test {
    @Test void acceptedPublicationDoesNotFinishGlobalWhileDeliveryIsActive() {
        var lifecycle = new ExecutionLifecycleV4();
        lifecycle.eventAt(0);
        lifecycle.open("message-1", ExecutionLifecycleV4.ResourceKind.BROKER_MESSAGE);
        lifecycle.open("delivery-1", ExecutionLifecycleV4.ResourceKind.DELIVERY);
        lifecycle.finishMain(ExecutionLifecycleV4.MainStatus.COMPLETED);
        lifecycle.finish("message-1", ExecutionLifecycleV4.ResourceStatus.COMPLETED);
        assertNull(lifecycle.globalStatus());
        lifecycle.eventAt(20);
        lifecycle.finish("delivery-1", ExecutionLifecycleV4.ResourceStatus.COMPLETED);
        assertEquals(ExecutionLifecycleV4.GlobalStatus.COMPLETED, lifecycle.globalStatus());
    }

    @Test void timedOutDerivedWorkCompletesGloballyWithErrors() {
        var lifecycle = new ExecutionLifecycleV4();
        lifecycle.open("delivery-1", ExecutionLifecycleV4.ResourceKind.DELIVERY);
        lifecycle.finishMain(ExecutionLifecycleV4.MainStatus.COMPLETED);
        lifecycle.eventAt(30_000);
        lifecycle.finish("delivery-1", ExecutionLifecycleV4.ResourceStatus.TIMED_OUT);
        assertEquals(ExecutionLifecycleV4.GlobalStatus.COMPLETED_WITH_ERRORS, lifecycle.globalStatus());
        assertEquals(30_000, lifecycle.totalDurationMs());
    }

    @Test void sessionBlocksGlobalUntilClose() {
        var lifecycle = new ExecutionLifecycleV4();
        lifecycle.open("session-1", ExecutionLifecycleV4.ResourceKind.WEBSOCKET_SESSION);
        assertNull(lifecycle.globalStatus());
        lifecycle.eventAt(12);
        lifecycle.finish("session-1", ExecutionLifecycleV4.ResourceStatus.COMPLETED);
        lifecycle.finishMain(ExecutionLifecycleV4.MainStatus.COMPLETED);
        assertEquals(ExecutionLifecycleV4.GlobalStatus.COMPLETED, lifecycle.globalStatus());
    }

    @Test void durationAndOrderingAreBounded() {
        var lifecycle = new ExecutionLifecycleV4();
        lifecycle.eventAt(1);
        assertThrows(IllegalArgumentException.class, () -> lifecycle.eventAt(0));
        assertThrows(IllegalArgumentException.class, () -> lifecycle.eventAt(3_600_001));
    }
}
