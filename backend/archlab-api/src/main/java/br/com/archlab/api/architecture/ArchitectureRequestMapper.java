package br.com.archlab.api.architecture;

import br.com.archlab.simulation.core.domain.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.*;

/**
 * Strict, discriminator-based JSON decoding. Structural JSON errors are HTTP 400;
 * numeric domain violations (negative durations) remain available to the validator.
 */
@Component
public class ArchitectureRequestMapper {
    public Architecture toDomain(JsonNode root) {
        ArchitectureInput input = decode(root);
        List<br.com.archlab.simulation.core.domain.Component> components = new ArrayList<>();
        for (ArchitectureInput.ComponentInput item : input.components()) {
            switch (item) {
                case ArchitectureInput.ClientInput c -> components.add(new ClientComponent(c.id()));
                case ArchitectureInput.ApiInput a -> components.add(new ApiComponent(a.id(), a.preProcessingMs(), a.postProcessingMs()));
                case ArchitectureInput.DatabaseInput d -> components.add(new DatabaseComponent(d.id(), d.processingMs()));
            }
        }
        List<Connection> connections = input.connections().stream()
                .map(c -> new Connection(c.id(), c.sourceId(), c.targetId(), Protocol.valueOf(c.protocol()), c.latencyMs()))
                .toList();
        return new Architecture(components, connections);
    }

    public ArchitectureInput decode(JsonNode root) {
        object(root, Set.of("components", "connections"));
        JsonNode componentArray = array(root, "components");
        JsonNode connectionArray = array(root, "connections");
        List<ArchitectureInput.ComponentInput> components = new ArrayList<>();
        for (JsonNode c : componentArray) {
            if (!c.isObject()) throw bad("components must contain objects");
            String type = str(c, "type");
            switch (type) {
                case "CLIENT" -> {
                    object(c, Set.of("id", "type"));
                    components.add(new ArchitectureInput.ClientInput(str(c, "id")));
                }
                case "API" -> {
                    object(c, Set.of("id", "type", "preProcessingMs", "postProcessingMs"));
                    components.add(new ArchitectureInput.ApiInput(str(c, "id"),
                            number(c, "preProcessingMs"), number(c, "postProcessingMs")));
                }
                case "DATABASE" -> {
                    object(c, Set.of("id", "type", "processingMs"));
                    components.add(new ArchitectureInput.DatabaseInput(str(c, "id"), number(c, "processingMs")));
                }
                default -> throw bad("Unsupported component type");
            }
        }
        List<ArchitectureInput.ConnectionInput> connections = new ArrayList<>();
        for (JsonNode c : connectionArray) {
            object(c, Set.of("id", "sourceId", "targetId", "protocol", "latencyMs"));
            String protocol = str(c, "protocol");
            if (!Set.of("REST", "SOAP", "TCP").contains(protocol)) throw bad("Unsupported protocol");
            connections.add(new ArchitectureInput.ConnectionInput(str(c, "id"),
                    str(c, "sourceId"), str(c, "targetId"), protocol, number(c, "latencyMs")));
        }
        return new ArchitectureInput(List.copyOf(components), List.copyOf(connections));
    }

    private static void object(JsonNode node, Set<String> keys) {
        if (node == null || !node.isObject()) throw bad("Expected JSON object");
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) if (!keys.contains(names.next())) throw bad("Unexpected property");
    }

    private static JsonNode array(JsonNode node, String field) {
        JsonNode result = node.get(field);
        if (result == null || !result.isArray()) throw bad("Field " + field + " must be an array");
        return result;
    }

    private static String str(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual()) throw bad("Field " + field + " must be a string");
        String text = value.asText();
        if (text.isBlank() || text.length() > 128) throw bad("Invalid identifier or value for " + field);
        return text;
    }

    private static long number(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToLong())
            throw bad("Field " + field + " must be an int64");
        return value.longValue();
    }

    private static IllegalArgumentException bad(String message) {
        return new IllegalArgumentException(message);
    }
}
