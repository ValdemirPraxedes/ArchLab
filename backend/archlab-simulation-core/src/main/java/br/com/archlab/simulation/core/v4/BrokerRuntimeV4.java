package br.com.archlab.simulation.core.v4;

import br.com.archlab.simulation.core.v2.*;
import java.util.*;

/** Finite in-memory messaging simulation with deterministic subscription order. */
public final class BrokerRuntimeV4 {
    public record Publication(int index, String id, long sentAtMs, long acceptedAtMs,
                              boolean timedOutWithoutConsumer) {}
    public record Delivery(int publicationIndex, String id, String messageId,
                           String connectionId, String consumerId,
                           long sentAtMs, long arrivedAtMs, long completedAtMs) {}
    public record Result(ExecutionLifecycleV4.MainStatus mainStatus,
                         ExecutionLifecycleV4.GlobalStatus status, long totalDurationMs,
                         List<Publication> publications, List<Delivery> deliveries,
                         int eligibleSubscribers, Map<String, ExecutionLifecycleV4.Resource> resources) {}

    public Result run(ArchitectureV2 architecture, ConnectionV2 entry, BrokerV2 broker,
                      String destinationName, int publishCount) {
        if (publishCount < 1 || publishCount > 100 || entry.latencyMs() < 0)
            throw new IllegalArgumentException("Invalid publication count or latency");
        IntegrationKindV2 kind = entry.integration().kind();
        if (!kind.isMessaging()) throw new IllegalArgumentException("Entry is not messaging");
        Map<String,ConnectionV2> connections = new HashMap<>();
        for (var connection : architecture.connections()) connections.put(connection.id(),connection);
        Map<String,ApiV2> apis = new HashMap<>();
        for (var component : architecture.components()) if (component instanceof ApiV2 api) apis.put(api.id(),api);
        List<BrokerSubscriptionV2> eligible = broker.subscriptions().stream()
                .filter(s -> destinationName.equals(s.destinationName())).toList();
        if (kind != IntegrationKindV2.QUEUE && (long)publishCount * eligible.size() > 1000)
            throw new IllegalArgumentException("Derived delivery limit exceeded");

        var lifecycle = new ExecutionLifecycleV4();
        List<Publication> publications = new ArrayList<>();
        List<Delivery> deliveries = new ArrayList<>();
        long accepted = entry.latencyMs();
        long last = accepted;
        for (int i=1; i<=publishCount; i++) {
            String messageId="broker-message-"+i;
            lifecycle.open(messageId, ExecutionLifecycleV4.ResourceKind.BROKER_MESSAGE);
            boolean pending = kind == IntegrationKindV2.QUEUE && eligible.isEmpty();
            publications.add(new Publication(i,messageId,0,accepted,pending));
            if (pending) {
                last = Math.max(last,Math.addExact(accepted,30_000L));
                continue;
            }
            lifecycle.finish(messageId,ExecutionLifecycleV4.ResourceStatus.COMPLETED);
            List<BrokerSubscriptionV2> recipients = kind == IntegrationKindV2.QUEUE
                    ? List.of(eligible.get((i-1)%eligible.size())) : eligible;
            for (BrokerSubscriptionV2 subscription : recipients) {
                ConnectionV2 connection = Objects.requireNonNull(connections.get(subscription.connectionId()));
                ApiV2 api = Objects.requireNonNull(apis.get(subscription.consumerComponentId()));
                String deliveryId="delivery-"+i+"-"+(deliveries.size()+1);
                lifecycle.open(deliveryId,ExecutionLifecycleV4.ResourceKind.DELIVERY);
                long arrived=Math.addExact(accepted,connection.latencyMs());
                long completed=Math.addExact(arrived,Math.addExact(api.preProcessingMs(),api.postProcessingMs()));
                last=Math.max(last,completed);
                deliveries.add(new Delivery(i,deliveryId,messageId,connection.id(),api.id(),accepted,arrived,completed));
            }
        }
        lifecycle.eventAt(0);
        lifecycle.eventAt(accepted);
        lifecycle.finishMain(ExecutionLifecycleV4.MainStatus.COMPLETED);
        for (Delivery delivery : deliveries.stream().sorted(Comparator.comparingLong(Delivery::completedAtMs)
                .thenComparing(Delivery::id)).toList()) {
            lifecycle.eventAt(delivery.completedAtMs());
            lifecycle.finish(delivery.id(),ExecutionLifecycleV4.ResourceStatus.COMPLETED);
        }
        if (kind == IntegrationKindV2.QUEUE && eligible.isEmpty()) {
            lifecycle.eventAt(last);
            for (Publication publication : publications)
                lifecycle.finish(publication.id(),ExecutionLifecycleV4.ResourceStatus.TIMED_OUT);
        }
        if (last > lifecycle.totalDurationMs()) lifecycle.eventAt(last);
        return new Result(lifecycle.mainStatus(),lifecycle.globalStatus(),lifecycle.totalDurationMs(),
                List.copyOf(publications),List.copyOf(deliveries),eligible.size(),lifecycle.resources());
    }
}
