package br.com.archlab.simulation.core.event;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EventQueueTest {
    private static SimulationEvent event(String id) {
        return new SimulationCompleted(id);
    }

    @Test
    void ordersByTimeThenInsertionSequence() {
        VirtualClock clock = new VirtualClock();
        EventQueue queue = new EventQueue(clock);
        ScheduledEvent later = queue.scheduleAt(20, event("later"));
        ScheduledEvent first = queue.scheduleAt(10, event("first"));
        ScheduledEvent second = queue.scheduleAt(10, event("second"));
        assertEquals(0L, later.sequence());
        assertEquals(1L, first.sequence());
        assertEquals(2L, second.sequence());

        assertEquals(first, queue.pollNext().orElseThrow());
        assertEquals(10, clock.nowMs());
        assertEquals(second, queue.pollNext().orElseThrow());
        assertEquals(later, queue.pollNext().orElseThrow());
        assertEquals(20, clock.nowMs());
        assertTrue(queue.pollNext().isEmpty());
    }

    @Test
    void permitsSchedulingDuringProcessingIncludingSameTimestamp() {
        VirtualClock clock = new VirtualClock();
        EventQueue queue = new EventQueue(clock);
        queue.scheduleAt(10, event("initial"));
        queue.scheduleAt(20, event("later"));
        List<String> visited = new ArrayList<>();
        while (!queue.isEmpty()) {
            ScheduledEvent next = queue.pollNext().orElseThrow();
            String id = ((SimulationCompleted) next.event()).requestId();
            visited.add(id);
            if (id.equals("initial")) {
                queue.scheduleAfter(0, event("same-instant"));
                queue.scheduleAfter(5, event("future"));
            }
        }
        assertEquals(List.of("initial", "same-instant", "future", "later"), visited);
        assertEquals(20, clock.nowMs());
    }

    @Test
    void rejectsNegativeOrRetroactiveTimesWithoutMutatingQueue() {
        VirtualClock clock = new VirtualClock();
        EventQueue queue = new EventQueue(clock);
        assertThrows(IllegalArgumentException.class, () -> queue.scheduleAt(-1, event("bad")));
        assertThrows(IllegalArgumentException.class, () -> queue.scheduleAfter(-1, event("bad")));
        queue.scheduleAt(10, event("valid"));
        queue.pollNext().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> queue.scheduleAt(9, event("past")));
        assertThrows(IllegalArgumentException.class, () -> clock.advanceTo(9));
        assertTrue(queue.isEmpty());
        assertEquals(10, clock.nowMs());
    }

    @Test
    void detectsDurationOverflowWithoutChangingTimeOrQueue() {
        VirtualClock clock = new VirtualClock();
        EventQueue queue = new EventQueue(clock);
        clock.advanceTo(Long.MAX_VALUE - 1);
        assertThrows(ArithmeticException.class, () -> queue.scheduleAfter(2, event("overflow")));
        assertEquals(Long.MAX_VALUE - 1, clock.nowMs());
        assertTrue(queue.isEmpty());
        queue.scheduleAfter(1, event("allowed"));
        assertEquals(Long.MAX_VALUE, queue.pollNext().orElseThrow().timestampMs());
    }

    @Test
    void refusesToConsumeEventsThatBecameRetroactive() {
        VirtualClock clock = new VirtualClock();
        EventQueue queue = new EventQueue(clock);
        queue.scheduleAt(10, event("first"));
        clock.advanceTo(11);
        assertThrows(IllegalArgumentException.class, queue::pollNext);
        assertEquals(1, queue.size());
    }

    @Test
    void rejectsNullEventsAndInvalidScheduledEvents() {
        EventQueue queue = new EventQueue(new VirtualClock());
        assertThrows(NullPointerException.class, () -> queue.scheduleAt(0, null));
        assertThrows(IllegalArgumentException.class, () -> new ScheduledEvent(0, -1, event("negative")));
        assertThrows(NullPointerException.class, () -> new ScheduledEvent(0, 0, null));
    }

    @Test
    void supportsAllGranularEventTypes() {
        List<SimulationEvent> types = List.of(
                new RequestSent("r", "client", "api"),
                new RequestArrived("r", "client", "api"),
                new ProcessingStarted("r", "api"),
                new ProcessingCompleted("r", "api"),
                new ResponseSent("r", "api", "client"),
                new ResponseArrived("r", "api", "client"),
                new SimulationCompleted("r"));
        EventQueue queue = new EventQueue(new VirtualClock());
        for (SimulationEvent event : types) queue.scheduleAt(0, event);
        for (SimulationEvent event : types) {
            assertEquals(event, queue.pollNext().orElseThrow().event());
        }
    }

    @Test
    void isDeterministicAcrossIndependentExecutions() {
        assertEquals(runScenario(), runScenario());
    }

    private List<ScheduledEvent> runScenario() {
        EventQueue queue = new EventQueue(new VirtualClock());
        queue.scheduleAt(30, event("a"));
        queue.scheduleAt(10, event("b"));
        queue.scheduleAt(10, event("c"));
        List<ScheduledEvent> result = new ArrayList<>();
        while (!queue.isEmpty()) result.add(queue.pollNext().orElseThrow());
        return result;
    }
}
