package br.com.archlab.simulation.core.event;

import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DiscreteEventLoopTest {
    @Test void runsEventsScheduledDuringDispatchInVirtualOrder() {
        VirtualClock clock = new VirtualClock();
        EventQueue queue = new EventQueue(clock);
        List<String> visited = new ArrayList<>();
        queue.scheduleAfter(5, new RequestSent("r", "c", "a"));
        DiscreteEventLoop.run(() -> !queue.isEmpty(), () -> queue.pollNext().orElseThrow(), scheduled -> {
            visited.add(scheduled.timestampMs() + ":" + scheduled.sequence());
            if (scheduled.event() instanceof RequestSent) {
                queue.scheduleAfter(0, new RequestArrived("r", "c", "a"));
            }
        }, 5);
        assertEquals(List.of("5:0", "5:1"), visited);
        assertEquals(5, clock.nowMs());
    }

    @Test void abortsWhenEventSafetyLimitIsExceeded() {
        VirtualClock clock = new VirtualClock();
        EventQueue queue = new EventQueue(clock);
        queue.scheduleAfter(0, new RequestSent("r", "c", "a"));
        assertThrows(IllegalStateException.class,
                () -> DiscreteEventLoop.run(() -> !queue.isEmpty(), () -> queue.pollNext().orElseThrow(),
                        scheduled -> queue.scheduleAfter(0, new RequestSent("r", "c", "a")), 3));
    }

    @Test void rejectsInvalidEventBudget() {
        assertThrows(IllegalArgumentException.class,
                () -> DiscreteEventLoop.run(() -> false, () -> null, event -> {}, 0));
    }
}
