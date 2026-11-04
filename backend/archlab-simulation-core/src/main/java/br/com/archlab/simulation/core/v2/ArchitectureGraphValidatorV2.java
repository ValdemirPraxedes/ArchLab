package br.com.archlab.simulation.core.v2;

import br.com.archlab.simulation.core.validation.ValidationResult;
import br.com.archlab.simulation.core.validation.Violation;
import java.util.*;

/**
 * Structural validation of the complete candidate architecture graph.
 * Intentionally independent of scenario selection, Spring and the v1 validator.
 * Each violation uses the stable public OpenAPI v2 code and JSON-style path.
 */
public final class ArchitectureGraphValidatorV2 {
    public static final long MAX_DURATION_MS = 1_000_000_000L;
    public static final int MAX_COMPONENTS = 1000;
    public static final int MAX_CONNECTIONS = 3000;

    public ValidationResult validate(ArchitectureV2 architecture) {
        Objects.requireNonNull(architecture, "architecture");
        List<Violation> errors = new ArrayList<>();
        Map<String, ComponentV2> components = new LinkedHashMap<>();
        Map<String, Integer> componentIndices = new HashMap<>();
        if (architecture.components().size() > MAX_COMPONENTS) {
            add(errors, "INVALID_TOPOLOGY", "components", "Component count exceeds limit");
        }
        if (architecture.connections().size() > MAX_CONNECTIONS) {
            add(errors, "INVALID_TOPOLOGY", "connections", "Connection count exceeds limit");
        }

        for (int i = 0; i < architecture.components().size(); i++) {
            ComponentV2 component = architecture.components().get(i);
            String path = "components[" + i + "]";
            if (!identifier(component.id())) {
                add(errors, "INVALID_IDENTIFIER", path + ".id", "Component ID must contain 1 to 128 characters");
            } else if (componentIndices.putIfAbsent(component.id(), i) != null) {
                add(errors, "DUPLICATE_COMPONENT_ID", path + ".id", "Component ID is already used");
            } else {
                components.put(component.id(), component);
            }
            if (!displayName(component.displayName())) {
                add(errors, "INVALID_DISPLAY_NAME", path + ".displayName",
                        "Display name must contain 1 to 80 Unicode characters");
            }
            if (component instanceof ApiV2 api) {
                duration(api.preProcessingMs(), path + ".preProcessingMs", errors);
                duration(api.postProcessingMs(), path + ".postProcessingMs", errors);
            } else if (component instanceof DatabaseV2 database) {
                duration(database.processingMs(), path + ".processingMs", errors);
            } else if (component instanceof BrokerV2 broker) {
                if (broker.destinations().size() > MAX_CONNECTIONS || broker.subscriptions().size() > MAX_CONNECTIONS)
                    add(errors, "INVALID_TOPOLOGY", path, "Broker configuration exceeds limit");
                Set<String> destinationNames = new HashSet<>();
                for (int j = 0; j < broker.destinations().size(); j++) {
                    var destination = broker.destinations().get(j);
                    if (!name(destination.name()) || !destinationNames.add(destination.name()))
                        add(errors, "INVALID_INTEGRATION", path + ".destinations[" + j + "]",
                                "Broker destination name is invalid or duplicated");
                }
            }
        }

        Set<String> connectionIds = new HashSet<>();
        Set<Set<String>> pairs = new HashSet<>();
        Map<String, Set<String>> graph = new LinkedHashMap<>();
        components.keySet().forEach(id -> graph.put(id, new LinkedHashSet<>()));
        for (int i = 0; i < architecture.connections().size(); i++) {
            ConnectionV2 connection = architecture.connections().get(i);
            String path = "connections[" + i + "]";
            if (!identifier(connection.id())) {
                add(errors, "INVALID_IDENTIFIER", path + ".id", "Connection ID must contain 1 to 128 characters");
            } else if (!connectionIds.add(connection.id())) {
                add(errors, "DUPLICATE_CONNECTION_ID", path + ".id", "Connection ID is already used");
            }
            duration(connection.latencyMs(), path + ".latencyMs", errors);
            ApiProcessingOverrideV2 override = connection.apiProcessingOverride();
            if (override != null) {
                duration(override.preProcessingMs(), path + ".apiProcessingOverride.preProcessingMs", errors);
                duration(override.postProcessingMs(), path + ".apiProcessingOverride.postProcessingMs", errors);
            }
            boolean fromOk = identifier(connection.sourceId());
            boolean toOk = identifier(connection.targetId());
            if (!fromOk) add(errors, "INVALID_IDENTIFIER", path + ".sourceId", "Source ID is invalid");
            if (!toOk) add(errors, "INVALID_IDENTIFIER", path + ".targetId", "Target ID is invalid");
            if (!fromOk || !toOk) continue;
            if (connection.sourceId().equals(connection.targetId())) {
                add(errors, "INVALID_CONNECTION", path, "A component cannot call itself");
                continue;
            }
            ComponentV2 source = components.get(connection.sourceId());
            ComponentV2 target = components.get(connection.targetId());
            if (source == null || target == null) {
                add(errors, "UNKNOWN_COMPONENT", path, "Connection references a missing component");
                continue;
            }
            if (!pairs.add(Set.of(connection.sourceId(), connection.targetId()))) {
                add(errors, "DUPLICATE_CONNECTION", path, "Only one connection per unordered component pair is allowed");
            }
            // Include every resolvable edge in the directed graph, even if its type is invalid.
            // This catches cycles outside the selected execution path.
            graph.get(connection.sourceId()).add(connection.targetId());
            boolean http = (source.type().isClient() && target.type() == ComponentTypeV2.API)
                    || (source.type() == ComponentTypeV2.API && target.type() == ComponentTypeV2.API);
            boolean db = source.type() == ComponentTypeV2.API && target.type() == ComponentTypeV2.DATABASE;
            boolean messaging = ((source.type().isClient() || source.type() == ComponentTypeV2.API)
                    && target.type() == ComponentTypeV2.BROKER)
                    || (source.type() == ComponentTypeV2.BROKER && target.type() == ComponentTypeV2.API);
            if (override != null && !http) {
                add(errors, "INVALID_INTEGRATION", path + ".apiProcessingOverride",
                        "Processing override is permitted only for connections entering an API");
            }
            if (!http && !db && !messaging) {
                add(errors, "INVALID_CONNECTION", path, "Connection endpoints are incompatible");
            }
            IntegrationV2 integration = connection.integration();
            if (integration == null || (http && integration.kind() == IntegrationKindV2.DATABASE_QUERY)
                    || (http && integration.kind().isMessaging())
                    || (db && integration.kind() != IntegrationKindV2.DATABASE_QUERY)
                    || (messaging && !integration.kind().isMessaging())) {
                add(errors, "INVALID_INTEGRATION", path + ".integration",
                        "Integration kind is incompatible with connection endpoints");
            } else if (messaging) {
                BrokerV2 broker = source instanceof BrokerV2 b ? b : (BrokerV2) target;
                String destinationName = destinationName(integration);
                boolean matched = broker.destinations().stream().anyMatch(d -> d.name().equals(destinationName)
                        && d.kind() == integration.kind());
                if (!matched || integration instanceof MqttIntegrationV2 mqtt
                        && (mqtt.qos() != 0 || mqtt.topic().contains("#") || mqtt.topic().contains("+"))) {
                    add(errors, "INVALID_INTEGRATION", path + ".integration",
                            "Messaging integration must match a broker destination and MQTT QoS 0");
                }
            }
        }

        for (int i = 0; i < architecture.components().size(); i++) {
            if (!(architecture.components().get(i) instanceof BrokerV2 broker)) continue;
            for (int j = 0; j < broker.subscriptions().size(); j++) {
                var subscription = broker.subscriptions().get(j);
                String path = "components[" + i + "].subscriptions[" + j + "]";
                BrokerDestinationV2 destination = broker.destinations().stream()
                        .filter(d -> d.name().equals(subscription.destinationName())).findFirst().orElse(null);
                ConnectionV2 connection = architecture.connections().stream()
                        .filter(c -> c.id().equals(subscription.connectionId())).findFirst().orElse(null);
                if (destination == null || connection == null
                        || !broker.id().equals(connection.sourceId())
                        || !subscription.consumerComponentId().equals(connection.targetId())
                        || !(components.get(subscription.consumerComponentId()) instanceof ApiV2)
                        || connection.integration() == null
                        || connection.integration().kind() != destination.kind()
                        || !destination.name().equals(destinationName(connection.integration()))) {
                    add(errors, "INVALID_INTEGRATION", path, "Broker subscription is inconsistent with graph");
                }
            }
        }

        if (hasCycle(graph)) {
            add(errors, "CYCLIC_DEPENDENCY", "connections",
                    "Directed cycle detected in the complete architecture graph");
        }
        return new ValidationResult(errors.isEmpty(), errors);
    }

    private static boolean identifier(String value) {
        return value != null && !value.isBlank() && value.codePointCount(0, value.length()) <= 128;
    }

    private static boolean displayName(String name) {
        if (name == null || name.isBlank()) return false;
        String trimmed = name.strip();
        return !trimmed.isEmpty() && trimmed.codePointCount(0, trimmed.length()) <= 80;
    }
    private static boolean name(String name) {
        return name != null && !name.isBlank() && name.codePointCount(0,name.length()) <= 256;
    }

    private static String destinationName(IntegrationV2 integration) {
        if (integration instanceof QueueIntegrationV2 queue) return queue.queueName();
        if (integration instanceof TopicIntegrationV2 topic) return topic.topic();
        if (integration instanceof MqttIntegrationV2 mqtt) return mqtt.topic();
        return null;
    }

    private static void duration(long ms, String path, List<Violation> errors) {
        if (ms < 0 || ms > MAX_DURATION_MS) {
            add(errors, "INVALID_DURATION", path, "Duration must be between 0 and 1000000000 ms");
        }
    }

    private static void add(List<Violation> errors, String code, String path, String message) {
        errors.add(new Violation(code, path, message));
    }

    /** Iterative three-colour DFS avoids recursion overflow on a 1000-node candidate. */
    private static boolean hasCycle(Map<String, Set<String>> graph) {
        Map<String, Integer> state = new HashMap<>();
        for (String start : graph.keySet()) {
            if (state.containsKey(start)) continue;
            state.put(start, 1);
            Deque<Frame> stack = new ArrayDeque<>();
            stack.push(new Frame(start, graph.get(start).iterator()));
            while (!stack.isEmpty()) {
                Frame top = stack.peek();
                if (!top.edges().hasNext()) {
                    state.put(top.node(), 2);
                    stack.pop();
                    continue;
                }
                String next = top.edges().next();
                int status = state.getOrDefault(next, 0);
                if (status == 1) return true;
                if (status == 0) {
                    state.put(next, 1);
                    stack.push(new Frame(next, graph.get(next).iterator()));
                }
            }
        }
        return false;
    }

    private record Frame(String node, Iterator<String> edges) {}
}
