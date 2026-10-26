package br.com.archlab.simulation.core.v2;

import br.com.archlab.simulation.core.validation.ValidationResult;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ScenarioValidatorV2Test {
    private final ScenarioValidatorV2 validator = new ScenarioValidatorV2();
    private static ClientV2 client(String id) { return new ClientV2(id, "Client " + id); }
    private static ApiV2 api(String id) { return new ApiV2(id, "API " + id, 10, 20); }
    private static DatabaseV2 db(String id) { return new DatabaseV2(id, "Database " + id, 50); }
    private static ConnectionV2 http(String id, String from, String to) {
        return new ConnectionV2(id, from, to, 5, new HttpRestIntegrationV2(null, null));
    }
    private static ConnectionV2 database(String id, String from, String to) {
        return new ConnectionV2(id, from, to, 5, new DatabaseQueryIntegrationV2(null, null));
    }
    private static ScenarioV2 scenario(String client, String entry, ApiExecutionV2... selections) {
        return new ScenarioV2(client, entry, List.of(selections));
    }
    private static void has(ValidationResult result, String code) {
        assertTrue(result.violations().stream().anyMatch(v -> v.code().equals(code)),
                () -> "Missing " + code + " from " + result.violations());
    }

    @Test void directApiResponseNeedsNoDatabaseOrApiExecution() {
        var architecture = new ArchitectureV2(List.of(client("c"), api("a")), List.of(http("ca", "c", "a")));
        assertTrue(validator.validate(architecture, scenario("c", "ca")).valid());
    }

    @Test void nestedApiThenDatabaseAcceptsOneDependencyPerApi() {
        var architecture = new ArchitectureV2(List.of(client("c"), api("a"), api("b"), db("d")),
                List.of(http("ca", "c", "a"), http("ab", "a", "b"), database("bd", "b", "d")));
        var result = validator.validate(architecture, scenario("c", "ca",
                new ApiExecutionV2("a", "ab"), new ApiExecutionV2("b", "bd")));
        assertTrue(result.valid(), () -> result.violations().toString());
    }

    @Test void multipleClientsSelectExactlyOneEntry() {
        var architecture = new ArchitectureV2(List.of(client("first"), client("second"), api("a")),
                List.of(http("one", "first", "a"), http("two", "second", "a")));
        assertTrue(validator.validate(architecture, scenario("second", "two")).valid());
        has(validator.validate(architecture, scenario("first", "two")), "INVALID_ENTRY_CONNECTION");
    }

    @Test void validInactiveApiSelectionsAreAllowedAndDoNotRequireReachability() {
        var architecture = new ArchitectureV2(List.of(client("c"), api("a"), api("unused"), db("d")),
                List.of(http("ca", "c", "a"), database("ud", "unused", "d")));
        assertTrue(validator.validate(architecture, scenario("c", "ca",
                new ApiExecutionV2("unused", "ud"))).valid());
    }

    @Test void missingSelectionMeansDirectReplyDespiteAvailableOutgoingEdges() {
        var architecture = new ArchitectureV2(List.of(client("c"), api("a"), api("b")),
                List.of(http("ca", "c", "a"), http("ab", "a", "b")));
        assertTrue(validator.validate(architecture, scenario("c", "ca")).valid());
    }

    @Test void rejectsNonClientEntryAndUnknownEntry() {
        var architecture = new ArchitectureV2(List.of(client("c"), api("a")), List.of(http("ca", "c", "a")));
        has(validator.validate(architecture, scenario("a", "ca")), "INVALID_ENTRY_COMPONENT");
        has(validator.validate(architecture, scenario("missing", "ca")), "INVALID_ENTRY_COMPONENT");
    }

    @Test void rejectsWrongDirectionUnknownAndMissingEntryConnection() {
        var architecture = new ArchitectureV2(List.of(client("c"), api("a"), api("b")),
                List.of(http("ca", "c", "a"), http("ab", "a", "b")));
        has(validator.validate(architecture, scenario("c", "ab")), "INVALID_ENTRY_CONNECTION");
        has(validator.validate(architecture, scenario("c", "missing")), "INVALID_ENTRY_CONNECTION");
        has(validator.validate(architecture, scenario("c", null)), "INVALID_ENTRY_CONNECTION");
    }

    @Test void rejectsDuplicateApiSelections() {
        var architecture = new ArchitectureV2(List.of(client("c"), api("a"), api("b")),
                List.of(http("ca", "c", "a"), http("ab", "a", "b")));
        has(validator.validate(architecture, scenario("c", "ca",
                new ApiExecutionV2("a", "ab"), new ApiExecutionV2("a", "ab"))),
                "DUPLICATE_API_EXECUTION");
    }

    @Test void rejectsSelectionFromClientOrDatabase() {
        var architecture = new ArchitectureV2(List.of(client("c"), api("a"), db("d")),
                List.of(http("ca", "c", "a"), database("ad", "a", "d")));
        has(validator.validate(architecture, scenario("c", "ca", new ApiExecutionV2("c", "ca"))),
                "INVALID_SCENARIO_DEPENDENCY");
        has(validator.validate(architecture, scenario("c", "ca", new ApiExecutionV2("d", "ad"))),
                "INVALID_SCENARIO_DEPENDENCY");
    }

    @Test void rejectsUnknownWrongSourceAndIncomingDependency() {
        var architecture = new ArchitectureV2(List.of(client("c"), api("a"), api("b"), db("d")),
                List.of(http("ca", "c", "a"), http("ab", "a", "b"), database("bd", "b", "d")));
        has(validator.validate(architecture, scenario("c", "ca", new ApiExecutionV2("a", "missing"))),
                "INVALID_SCENARIO_DEPENDENCY");
        has(validator.validate(architecture, scenario("c", "ca", new ApiExecutionV2("b", "ab"))),
                "INVALID_SCENARIO_DEPENDENCY");
        has(validator.validate(architecture, scenario("c", "ca", new ApiExecutionV2("a", "ca"))),
                "INVALID_SCENARIO_DEPENDENCY");
    }

    @Test void validatesArchitectureOutsideSelectedPath() {
        var architecture = new ArchitectureV2(List.of(client("c"), api("a"), api("x"), api("y")),
                List.of(http("ca", "c", "a"), http("xy", "x", "y"), http("yx", "y", "x")));
        has(validator.validate(architecture, scenario("c", "ca")), "CYCLIC_DEPENDENCY");
    }

    @Test void invalidScenarioDoesNotMutateArchitecture() {
        List<ComponentV2> components = List.of(client("c"), api("a"));
        List<ConnectionV2> connections = List.of(http("ca", "c", "a"));
        var architecture = new ArchitectureV2(components, connections);
        var selection = new ArrayList<ApiExecutionV2>(List.of(new ApiExecutionV2("a", "missing")));
        var candidate = new ScenarioV2("c", "ca", selection);
        selection.clear();
        has(validator.validate(architecture, candidate), "INVALID_SCENARIO_DEPENDENCY");
        assertEquals(2, architecture.components().size());
        assertEquals(1, architecture.connections().size());
        assertEquals(1, candidate.apiExecutions().size());
        assertThrows(UnsupportedOperationException.class, () -> candidate.apiExecutions().clear());
    }

    @Test void rejectsMoreThanThousandApiConfigurations() {
        var architecture = new ArchitectureV2(List.of(client("c"), api("a")), List.of(http("ca", "c", "a")));
        List<ApiExecutionV2> selections = new ArrayList<>();
        for (int i = 0; i < 1001; i++) selections.add(new ApiExecutionV2("a", "ca"));
        has(validator.validate(architecture, new ScenarioV2("c", "ca", selections)), "INVALID_TOPOLOGY");
    }

    @Test void errorsHaveStablePathsAndRequireNoEngine() {
        var architecture = new ArchitectureV2(List.of(client("c"), api("a")), List.of(http("ca", "c", "a")));
        var result = validator.validate(architecture, scenario("", "", new ApiExecutionV2("?", "?")));
        assertFalse(result.valid());
        assertEquals("INVALID_ENTRY_COMPONENT", result.violations().get(0).code());
        assertEquals("scenario.entryComponentId", result.violations().get(0).path());
        assertEquals("INVALID_ENTRY_CONNECTION", result.violations().get(1).code());
        assertEquals("scenario.entryConnectionId", result.violations().get(1).path());
    }
}
