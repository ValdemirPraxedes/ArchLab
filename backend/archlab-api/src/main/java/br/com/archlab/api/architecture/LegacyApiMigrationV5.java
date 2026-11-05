package br.com.archlab.api.architecture;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;

/** Explicit, atomic conversion of historical API nodes to the v0.5 SERVICE type. */
public final class LegacyApiMigrationV5 {
    private LegacyApiMigrationV5() {}

    public record Report(int convertedCount, List<String> componentIds) {
        public Report { componentIds = List.copyOf(componentIds); }
    }
    public record Result(JsonNode document, Report report) {}

    public static Result migrate(JsonNode input) {
        if (input == null || !input.isObject())
            throw new IllegalArgumentException("Expected document object at $");
        JsonNode architecture = input.has("architecture") ? input.get("architecture") : input;
        if (architecture == null || !architecture.isObject())
            throw new IllegalArgumentException("Expected architecture object at $.architecture");
        JsonNode components = architecture.get("components");
        if (components == null || !components.isArray())
            throw new IllegalArgumentException("Expected components array at $.architecture.components");

        JsonNode copy = input.deepCopy();
        JsonNode destination = copy.has("architecture") ? copy.get("architecture") : copy;
        JsonNode nodes = destination.get("components");
        List<String> converted = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            JsonNode component = nodes.get(i);
            String path = "$.architecture.components[" + i + "]";
            if (!(component instanceof ObjectNode object))
                throw new IllegalArgumentException("Expected component object at " + path);
            JsonNode type = object.get("type");
            JsonNode id = object.get("id");
            if (type == null || !type.isTextual() || id == null || !id.isTextual()
                    || id.asText().isBlank())
                throw new IllegalArgumentException("Missing type or id at " + path);
            if ("API".equals(type.asText())) {
                object.put("type", "SERVICE");
                converted.add(id.asText());
            }
        }
        return new Result(copy, new Report(converted.size(), converted));
    }
}
