package br.com.archlab.simulation.core.validation;

import br.com.archlab.simulation.core.domain.*;
import java.util.*;

public final class ArchitectureValidator {
    public ValidationResult validate(Architecture architecture) {
        Objects.requireNonNull(architecture, "architecture");
        List<Violation> violations = new ArrayList<>();
        Map<String, Component> byId = new HashMap<>();
        EnumMap<ComponentType, Integer> counts = new EnumMap<>(ComponentType.class);
        for (ComponentType type : ComponentType.values()) counts.put(type, 0);

        for (int i = 0; i < architecture.components().size(); i++) {
            Component component = architecture.components().get(i);
            String path = "components[" + i + "]";
            if (blank(component.id())) {
                violations.add(new Violation("INVALID_CONNECTION", path + ".id", "Component ID must not be blank"));
            } else if (byId.putIfAbsent(component.id(), component) != null) {
                violations.add(new Violation("DUPLICATE_COMPONENT_ID", path + ".id", "Component ID already exists"));
            }
            counts.merge(component.type(), 1, Integer::sum);
            if (component instanceof ApiComponent api) {
                duration(api.preProcessingMs(), path + ".preProcessingMs", violations);
                duration(api.postProcessingMs(), path + ".postProcessingMs", violations);
            } else if (component instanceof DatabaseComponent db) {
                duration(db.processingMs(), path + ".processingMs", violations);
            }
        }
        for (ComponentType type : ComponentType.values()) {
            int count = counts.get(type);
            if (count != 1) {
                String code = count == 0 ? "MISSING_COMPONENT" : "INVALID_COMPONENT_COUNT";
                violations.add(new Violation(code, "components", "Exactly one " + type + " is required",
                        Map.of("componentType", type.name(), "expected", 1, "actual", count)));
            }
        }

        Set<String> connectionIds = new HashSet<>();
        Set<Set<String>> pairs = new HashSet<>();
        Set<String> validPaths = new HashSet<>();
        for (int i = 0; i < architecture.connections().size(); i++) {
            Connection connection = architecture.connections().get(i);
            String path = "connections[" + i + "]";
            if (blank(connection.id())) {
                violations.add(new Violation("INVALID_CONNECTION", path + ".id", "Connection ID must not be blank"));
            } else if (!connectionIds.add(connection.id())) {
                violations.add(new Violation("DUPLICATE_CONNECTION_ID", path + ".id", "Connection ID already exists"));
            }
            duration(connection.latencyMs(), path + ".latencyMs", violations);
            if (blank(connection.sourceId()) || blank(connection.targetId())
                    || Objects.equals(connection.sourceId(), connection.targetId())) {
                violations.add(new Violation("INVALID_CONNECTION", path, "Connection needs distinct, nonblank endpoints"));
                continue;
            }
            Component source = byId.get(connection.sourceId());
            Component target = byId.get(connection.targetId());
            if (source == null || target == null) {
                violations.add(new Violation("UNKNOWN_COMPONENT", path, "Connection references an unknown component"));
                continue;
            }
            if (!pairs.add(Set.of(source.id(), target.id()))) {
                violations.add(new Violation("DUPLICATE_CONNECTION", path, "Only one connection per unordered component pair"));
            }
            boolean clientApi = source.type() == ComponentType.CLIENT && target.type() == ComponentType.API;
            boolean apiDb = source.type() == ComponentType.API && target.type() == ComponentType.DATABASE;
            if (!clientApi && !apiDb) {
                violations.add(new Violation("INVALID_CONNECTION", path, "Only CLIENT->API and API->DATABASE are supported"));
            } else {
                if (connection.protocol() == null
                        || (clientApi && connection.protocol() != Protocol.REST && connection.protocol() != Protocol.SOAP)
                        || (apiDb && connection.protocol() != Protocol.TCP)) {
                    violations.add(new Violation("INVALID_PROTOCOL", path + ".protocol", "Protocol is incompatible with the connection"));
                } else {
                    validPaths.add(clientApi ? "CLIENT_API" : "API_DATABASE");
                }
            }
        }
        if (!validPaths.contains("CLIENT_API")) {
            violations.add(new Violation("MISSING_CONNECTION", "connections", "CLIENT->API connection is required"));
        }
        if (!validPaths.contains("API_DATABASE")) {
            violations.add(new Violation("MISSING_CONNECTION", "connections", "API->DATABASE connection is required"));
        }
        return new ValidationResult(violations.isEmpty(), violations);
    }

    public Architecture requireValid(Architecture architecture) {
        ValidationResult result = validate(architecture);
        if (!result.valid()) throw new InvalidArchitectureException(result);
        return architecture;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static void duration(long value, String path, List<Violation> violations) {
        if (value < 0) violations.add(new Violation("INVALID_DURATION", path, "Duration must be nonnegative"));
    }
}
