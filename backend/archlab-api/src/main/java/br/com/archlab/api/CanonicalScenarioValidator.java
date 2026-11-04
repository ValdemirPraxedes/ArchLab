package br.com.archlab.api;

import br.com.archlab.api.v2.V2RequestMapper;
import br.com.archlab.simulation.core.v2.*;
import br.com.archlab.simulation.core.validation.*;
import tools.jackson.databind.JsonNode;

import java.util.*;

/** Validates the two v0.4 scenario shapes whose engines are implemented in later cards. */
final class CanonicalScenarioValidator {
    private final ArchitectureGraphValidatorV2 graph = new ArchitectureGraphValidatorV2();

    ValidationResult validate(JsonNode root, V2RequestMapper mapper) {
        if (root == null || !allFields(root, Set.of("architecture", "scenario")))
            throw new IllegalArgumentException("Invalid simulation request");
        JsonNode architectureNode = required(root, "architecture");
        JsonNode scenario = required(root, "scenario");
        ArchitectureV2 architecture = mapper.canonicalArchitecture(architectureNode);
        String kind = text(scenario, "kind");
        if ("WEBSOCKET_SESSION".equals(kind))
            return validateWebSocket(architecture, scenario);
        if ("MESSAGE_PUBLISH".equals(kind))
            return validatePublish(architecture, scenario);
        throw new IllegalArgumentException("Unsupported scenario.kind");
    }

    private ValidationResult validateWebSocket(ArchitectureV2 architecture, JsonNode scenario) {
        if (!allFields(scenario, Set.of("kind", "entryComponentId", "entryConnectionId")))
            throw new IllegalArgumentException("Unexpected WebSocket scenario field");
        String entryComponentId = text(scenario, "entryComponentId");
        String entryConnectionId = text(scenario, "entryConnectionId");
        List<Violation> errors = new ArrayList<>(graph.validate(architecture).violations());
        Map<String, ComponentV2> components = components(architecture);
        ComponentV2 initiator = components.get(entryComponentId);
        if (!(initiator instanceof ClientV2 || initiator instanceof ApiV2))
            add(errors, "INVALID_ENTRY_COMPONENT", "scenario.entryComponentId", "Entry must be Client or API");
        ConnectionV2 entry = connection(architecture, entryConnectionId);
        if (entry == null || !entryComponentId.equals(entry.sourceId())
                || !(components.get(entry.targetId()) instanceof ApiV2)
                || !(entry.integration() instanceof WebSocketIntegrationV2 websocket)) {
            add(errors, "INVALID_ENTRY_CONNECTION", "scenario.entryConnectionId", "Entry must use WebSocket into API");
        } else {
            if (websocket.connectTimeoutMs() < 0 || websocket.idleTimeoutMs() != null && websocket.idleTimeoutMs() < 0)
                add(errors, "INVALID_DURATION", "architecture.connections", "WebSocket timeout must be nonnegative");
            for (var message : websocket.messages()) if (message.intervalMs() < 0)
                add(errors, "INVALID_DURATION", "architecture.connections", "Message interval must be nonnegative");
        }
        return new ValidationResult(errors.isEmpty(), errors);
    }

    private ValidationResult validatePublish(ArchitectureV2 architecture, JsonNode scenario) {
        if (!allFields(scenario, Set.of("kind", "entryComponentId", "entryConnectionId", "destinationName", "publishCount")))
            throw new IllegalArgumentException("Unexpected publish scenario field");
        String entryComponentId = text(scenario, "entryComponentId");
        String entryConnectionId = text(scenario, "entryConnectionId");
        String destinationName = text(scenario, "destinationName");
        if (destinationName.codePointCount(0,destinationName.length()) > 256)
            throw new IllegalArgumentException("destinationName exceeds 256 characters");
        JsonNode countNode = scenario.get("publishCount");
        if (countNode != null && (!countNode.isIntegralNumber() || countNode.asInt() < 1 || countNode.asInt() > 100))
            throw new IllegalArgumentException("publishCount must be between 1 and 100");
        List<Violation> errors = new ArrayList<>(graph.validate(architecture).violations());
        Map<String, ComponentV2> components = components(architecture);
        ComponentV2 initiator = components.get(entryComponentId);
        if (!(initiator instanceof ClientV2 || initiator instanceof ApiV2))
            add(errors, "INVALID_ENTRY_COMPONENT", "scenario.entryComponentId", "Entry must be Client or API");
        ConnectionV2 entry = connection(architecture, entryConnectionId);
        if (entry == null || !entryComponentId.equals(entry.sourceId())
                || !(components.get(entry.targetId()) instanceof BrokerV2 broker)
                || entry.integration() == null || !entry.integration().kind().isMessaging()) {
            add(errors, "INVALID_ENTRY_CONNECTION", "scenario.entryConnectionId", "Entry must use messaging into Broker");
        } else if (broker.destinations().stream().noneMatch(d -> d.name().equals(destinationName)
                && d.kind() == entry.integration().kind())) {
            add(errors, "INVALID_SCENARIO_DEPENDENCY", "scenario.destinationName", "Destination is absent or incompatible");
        } else {
            long subscribers = broker.subscriptions().stream()
                    .filter(s -> s.destinationName().equals(destinationName)).count();
            long publications = countNode == null ? 1 : countNode.asLong();
            if (entry.integration().kind() != IntegrationKindV2.QUEUE && subscribers * publications > 1000)
                add(errors, "INVALID_TOPOLOGY", "scenario.publishCount", "Derived deliveries exceed 1000");
        }
        return new ValidationResult(errors.isEmpty(), errors);
    }

    private static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) throw new IllegalArgumentException("Missing field: " + field);
        return value;
    }
    private static String text(JsonNode node, String field) {
        JsonNode value = required(node, field);
        if (!value.isTextual() || value.asText().isBlank()) throw new IllegalArgumentException("Invalid field: " + field);
        return value.asText();
    }
    private static boolean allFields(JsonNode node, Set<String> allowed) {
        if (!node.isObject()) return false;
        for (String name : node.propertyNames()) if (!allowed.contains(name)) return false;
        return true;
    }
    private static Map<String, ComponentV2> components(ArchitectureV2 architecture) {
        Map<String, ComponentV2> result = new HashMap<>();
        for (var component : architecture.components()) result.putIfAbsent(component.id(), component);
        return result;
    }
    private static ConnectionV2 connection(ArchitectureV2 architecture, String id) {
        return architecture.connections().stream().filter(c -> c.id().equals(id)).findFirst().orElse(null);
    }
    private static void add(List<Violation> errors, String code, String path, String message) {
        errors.add(new Violation(code, path, message));
    }
}
