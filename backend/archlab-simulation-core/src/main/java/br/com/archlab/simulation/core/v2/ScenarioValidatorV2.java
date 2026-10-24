package br.com.archlab.simulation.core.v2;

import br.com.archlab.simulation.core.validation.ValidationResult;
import br.com.archlab.simulation.core.validation.Violation;
import java.util.*;

/**
 * Validates the complete architecture and the selected scenario, without running DES.
 * Structural violations are retained even for nodes outside the selected execution path.
 * The scenario never mutates the graph.
 */
public final class ScenarioValidatorV2 {
    private final ArchitectureGraphValidatorV2 architectureValidator = new ArchitectureGraphValidatorV2();

    public ValidationResult validate(ArchitectureV2 architecture, ScenarioV2 scenario) {
        Objects.requireNonNull(architecture, "architecture");
        Objects.requireNonNull(scenario, "scenario");
        List<Violation> errors = new ArrayList<>(architectureValidator.validate(architecture).violations());

        Map<String, ComponentV2> components = new LinkedHashMap<>();
        for (ComponentV2 c : architecture.components()) {
            if (c.id() != null) components.putIfAbsent(c.id(), c);
        }
        Map<String, ConnectionV2> connections = new LinkedHashMap<>();
        for (ConnectionV2 c : architecture.connections()) {
            if (c.id() != null) connections.putIfAbsent(c.id(), c);
        }

        ComponentV2 initiator = components.get(scenario.entryComponentId());
        if (!(initiator instanceof ClientV2) || !identifier(scenario.entryComponentId())) {
            add(errors, "INVALID_ENTRY_COMPONENT", "scenario.entryComponentId",
                    "Entry component must reference an existing Client");
        }
        ConnectionV2 entry = connections.get(scenario.entryConnectionId());
        if (!identifier(scenario.entryConnectionId()) || entry == null
                || !(components.get(entry.targetId()) instanceof ApiV2)
                || !Objects.equals(entry.sourceId(), scenario.entryComponentId())) {
            add(errors, "INVALID_ENTRY_CONNECTION", "scenario.entryConnectionId",
                    "Entry connection must lead from the selected Client to an API");
        }

        if (scenario.apiExecutions().size() > 1000) {
            add(errors, "INVALID_TOPOLOGY", "scenario.apiExecutions",
                    "At most 1000 API execution selections are allowed");
        }
        Set<String> selectedApis = new HashSet<>();
        for (int i = 0; i < scenario.apiExecutions().size(); i++) {
            ApiExecutionV2 selection = scenario.apiExecutions().get(i);
            String base = "scenario.apiExecutions[" + i + "]";
            if (!identifier(selection.componentId())
                    || !(components.get(selection.componentId()) instanceof ApiV2)) {
                add(errors, "INVALID_SCENARIO_DEPENDENCY", base + ".componentId",
                        "Selection must reference an existing API");
            }
            if (selection.componentId() != null && !selectedApis.add(selection.componentId())) {
                add(errors, "DUPLICATE_API_EXECUTION", base + ".componentId",
                        "Only one dependency selection per API is allowed");
            }

            ConnectionV2 dependency = connections.get(selection.dependencyConnectionId());
            if (!identifier(selection.dependencyConnectionId()) || dependency == null
                    || !Objects.equals(dependency.sourceId(), selection.componentId())
                    || !(components.get(dependency.targetId()) instanceof ApiV2
                        || components.get(dependency.targetId()) instanceof DatabaseV2)) {
                add(errors, "INVALID_SCENARIO_DEPENDENCY", base + ".dependencyConnectionId",
                        "Dependency must be an outgoing API-to-API or API-to-Database connection");
            }
        }
        return new ValidationResult(errors.isEmpty(), errors);
    }

    private static boolean identifier(String s) {
        return s != null && !s.isBlank() && s.codePointCount(0, s.length()) <= 128;
    }

    private static void add(List<Violation> errors, String code, String path, String message) {
        errors.add(new Violation(code, path, message));
    }
}
