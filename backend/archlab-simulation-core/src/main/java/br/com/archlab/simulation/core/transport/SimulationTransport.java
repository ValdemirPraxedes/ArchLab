package br.com.archlab.simulation.core.transport;

import br.com.archlab.simulation.core.domain.*;
import br.com.archlab.simulation.core.event.*;
import java.util.*;

/**
 * Virtual network: checks the directed outbound link and corresponding reverse
 * response; never performs I/O or invokes the recipient.
 */
public final class SimulationTransport {
    private final Map<String, Connection> links = new HashMap<>();
    private final IdentityHashMap<SimulationEvent, SimulationMessage> inFlight = new IdentityHashMap<>();
    private final Map<String, Exchange> exchanges = new HashMap<>();
    private final List<MessageTraceEntry> trace = new ArrayList<>();
    private long messageSequence;
    private long traceSequence;

    public SimulationTransport(Architecture architecture) {
        Objects.requireNonNull(architecture);
        for (Connection connection : architecture.connections()) {
            links.put(key(connection.sourceId(), connection.targetId()), connection);
        }
    }

    public void sendRequest(String requestId, String sourceId, String targetId,
                            MessageIntent intent, VirtualClock clock, EventQueue queue) {
        Connection connection = requireLink(sourceId, targetId);
        String exchangeId = switch (intent) {
            case API_REQUEST -> "exchange-api-1";
            case DATABASE_QUERY -> "exchange-db-1";
        };
        if (exchanges.containsKey(exchangeId)) throw new IllegalStateException("Duplicate exchange: " + exchangeId);
        SimulationMessage message = message(requestId, exchangeId, sourceId, targetId, MessageDirection.REQUEST, intent);
        RequestArrived arrival = new RequestArrived(requestId, sourceId, targetId);
        queue.scheduleAt(clock.after(connection.latencyMs()), arrival);
        exchanges.put(exchangeId, new Exchange(requestId, sourceId, targetId, intent));
        inFlight.put(arrival, message);
        record(clock.nowMs(), MessageTraceEntry.Action.SENT, connection.id(), message);
    }

    public void sendResponse(String requestId, String sourceId, String targetId,
                             MessageIntent intent, VirtualClock clock, EventQueue queue) {
        String exchangeId = intent == MessageIntent.API_REQUEST ? "exchange-api-1" : "exchange-db-1";
        Exchange exchange = exchanges.get(exchangeId);
        if (exchange == null || !exchange.requestArrived || exchange.responseSent
                || !exchange.requestId.equals(requestId) || !exchange.source.equals(targetId) || !exchange.target.equals(sourceId)
                || exchange.intent != intent) {
            throw new IllegalStateException("Unexpected or duplicate response: " + exchangeId);
        }
        Connection connection = requireLink(targetId, sourceId);
        SimulationMessage message = message(requestId, exchangeId, sourceId, targetId, MessageDirection.RESPONSE, intent);
        ResponseArrived arrival = new ResponseArrived(requestId, sourceId, targetId);
        queue.scheduleAt(clock.after(connection.latencyMs()), arrival);
        exchange.responseSent = true;
        inFlight.put(arrival, message);
        record(clock.nowMs(), MessageTraceEntry.Action.SENT, connection.id(), message);
    }

    public SimulationMessage arrive(SimulationEvent event, long nowMs) {
        SimulationMessage message = inFlight.remove(event);
        if (message == null) throw new IllegalStateException("Unexpected or duplicate message arrival");
        Exchange exchange = Objects.requireNonNull(exchanges.get(message.exchangeId()));
        if (message.direction() == MessageDirection.REQUEST) {
            if (exchange.requestArrived) throw new IllegalStateException("Duplicate request arrival");
            exchange.requestArrived = true;
        } else {
            if (!exchange.responseSent || exchange.responseArrived)
                throw new IllegalStateException("Unmatched response arrival");
            exchange.responseArrived = true;
        }
        Connection connection = requireLink(exchange.source, exchange.target);
        record(nowMs, MessageTraceEntry.Action.ARRIVED, connection.id(), message);
        return message;
    }

    public boolean completed(MessageIntent intent) {
        Exchange exchange = exchanges.get(intent == MessageIntent.API_REQUEST ? "exchange-api-1" : "exchange-db-1");
        return exchange != null && exchange.responseArrived;
    }

    public List<MessageTraceEntry> trace() { return List.copyOf(trace); }

    private SimulationMessage message(String requestId, String exchangeId, String sourceId,
                                      String targetId, MessageDirection direction, MessageIntent intent) {
        if (messageSequence == Long.MAX_VALUE) throw new ArithmeticException("Message sequence overflow");
        return new SimulationMessage("message-" + ++messageSequence, requestId, exchangeId,
                sourceId, targetId, direction, intent);
    }

    private void record(long timestamp, MessageTraceEntry.Action action,
                        String connectionId, SimulationMessage message) {
        trace.add(new MessageTraceEntry(timestamp, traceSequence++, action, connectionId, message));
    }

    private Connection requireLink(String sourceId, String targetId) {
        Connection connection = links.get(key(sourceId, targetId));
        if (connection == null) throw new IllegalStateException("Missing outbound link: " + sourceId + " -> " + targetId);
        return connection;
    }

    private static String key(String sourceId, String targetId) {
        return sourceId.length() + ":" + sourceId + targetId;
    }

    private static final class Exchange {
        private final String requestId, source, target;
        private final MessageIntent intent;
        private boolean requestArrived, responseSent, responseArrived;
        private Exchange(String requestId, String source, String target, MessageIntent intent) {
            this.requestId = requestId;
            this.source = source; this.target = target; this.intent = intent;
        }
    }
}
