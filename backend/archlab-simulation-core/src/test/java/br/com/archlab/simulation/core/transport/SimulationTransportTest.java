package br.com.archlab.simulation.core.transport;

import br.com.archlab.simulation.core.domain.*;
import br.com.archlab.simulation.core.event.*;
import br.com.archlab.simulation.core.engine.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class SimulationTransportTest {
    private Architecture architecture() {
        return new Architecture(
                List.of(new ClientComponent("client"), new ApiComponent("api", 30, 20), new DatabaseComponent("db", 150)),
                List.of(new Connection("ca", "client", "api", Protocol.REST, 20),
                        new Connection("ad", "api", "db", Protocol.TCP, 10)));
    }

    @Test void roundtripPreservesConnectionAndExchange() {
        SimulationResult result = new SimulationEngine().execute(architecture());
        assertEquals(260, result.totalDurationMs());
        assertEquals(8, result.messageTrace().size());
        List<MessageTraceEntry> messages = result.messageTrace();
        assertEquals(List.of(0L, 20L, 50L, 60L, 210L, 220L, 240L, 260L),
                messages.stream().map(MessageTraceEntry::timestampMs).toList());
        assertEquals(List.of(MessageTraceEntry.Action.SENT, MessageTraceEntry.Action.ARRIVED,
                MessageTraceEntry.Action.SENT, MessageTraceEntry.Action.ARRIVED,
                MessageTraceEntry.Action.SENT, MessageTraceEntry.Action.ARRIVED,
                MessageTraceEntry.Action.SENT, MessageTraceEntry.Action.ARRIVED),
                messages.stream().map(MessageTraceEntry::action).toList());
        assertEquals(List.of("ca", "ca", "ad", "ad", "ad", "ad", "ca", "ca"),
                messages.stream().map(MessageTraceEntry::connectionId).toList());
        assertEquals(messages.get(2).message().exchangeId(), messages.get(5).message().exchangeId());
        assertEquals(MessageDirection.REQUEST, messages.get(2).message().direction());
        assertEquals(MessageDirection.RESPONSE, messages.get(5).message().direction());
        assertEquals(MessageIntent.DATABASE_QUERY, messages.get(5).message().intent());
        assertNotEquals(messages.get(2).message().id(), messages.get(5).message().id());
        assertTrue(result.messageTrace().stream().allMatch(m -> m.message().requestId().equals("request-1")));
    }

    @Test void invalidOrDuplicateResponseIsRejected() {
        VirtualClock clock = new VirtualClock();
        EventQueue queue = new EventQueue(clock);
        SimulationTransport transport = new SimulationTransport(architecture());
        assertThrows(IllegalStateException.class, () ->
                transport.sendResponse("request-1", "db", "api", MessageIntent.DATABASE_QUERY, clock, queue));
        transport.sendRequest("request-1", "api", "db", MessageIntent.DATABASE_QUERY, clock, queue);
        assertThrows(IllegalStateException.class, () ->
                transport.sendResponse("request-1", "db", "api", MessageIntent.DATABASE_QUERY, clock, queue));
        SimulationEvent arriving = queue.pollNext().orElseThrow().event();
        transport.arrive(arriving, clock.nowMs());
        transport.sendResponse("request-1", "db", "api", MessageIntent.DATABASE_QUERY, clock, queue);
        assertThrows(IllegalStateException.class, () ->
                transport.sendResponse("request-1", "db", "api", MessageIntent.DATABASE_QUERY, clock, queue));
        transport.arrive(queue.pollNext().orElseThrow().event(), clock.nowMs());
        assertTrue(transport.completed(MessageIntent.DATABASE_QUERY));
        assertThrows(IllegalStateException.class, () -> transport.arrive(arriving, clock.nowMs()));
    }

    @Test void rejectsMissingOrWrongDirectionLink() {
        VirtualClock clock = new VirtualClock();
        EventQueue queue = new EventQueue(clock);
        SimulationTransport transport = new SimulationTransport(architecture());
        assertThrows(IllegalStateException.class, () ->
                transport.sendRequest("r", "db", "api", MessageIntent.DATABASE_QUERY, clock, queue));
        assertThrows(IllegalStateException.class, () ->
                transport.sendRequest("r", "client", "db", MessageIntent.API_REQUEST, clock, queue));
    }

    @Test void transportOverflowDoesNotEnqueue() {
        VirtualClock clock = new VirtualClock();
        clock.advanceTo(Long.MAX_VALUE);
        EventQueue queue = new EventQueue(clock);
        SimulationTransport transport = new SimulationTransport(architecture());
        assertThrows(ArithmeticException.class, () ->
                transport.sendRequest("r", "client", "api", MessageIntent.API_REQUEST, clock, queue));
        assertTrue(queue.isEmpty());
        assertTrue(transport.trace().isEmpty());
    }

    @Test void rejectsUnmatchedArrival() {
        SimulationTransport transport = new SimulationTransport(architecture());
        assertThrows(IllegalStateException.class, () ->
                transport.arrive(new ResponseArrived("r", "db", "api"), 0));
    }
}
