package br.com.archlab.simulation.core.v2;

import br.com.archlab.simulation.core.validation.ValidationResult;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectureGraphValidatorV2Test {
    private final ArchitectureGraphValidatorV2 validator = new ArchitectureGraphValidatorV2();
    private static ClientV2 client(String id) { return new ClientV2(id, "Client " + id); }
    private static ApiV2 api(String id) { return new ApiV2(id, "API " + id, 10, 5); }
    private static DatabaseV2 db(String id) { return new DatabaseV2(id, "DB " + id, 50); }
    private static HttpRestIntegrationV2 rest() { return new HttpRestIntegrationV2(null, null); }
    private static ConnectionV2 link(String id, String source, String target) {
        return new ConnectionV2(id, source, target, 5, rest());
    }
    private ValidationResult check(List<ComponentV2> components, ConnectionV2... links) {
        return validator.validate(new ArchitectureV2(components, List.of(links)));
    }
    private static void contains(ValidationResult result, String code) {
        assertTrue(result.violations().stream().anyMatch(v -> v.code().equals(code)),
                () -> "Expected " + code + " among " + result.violations());
    }

    @Test void acceptsMultipleClientsApisAndNoDatabase() {
        assertTrue(check(List.of(client("c1"), client("c2"), api("a"), api("b")),
                link("x", "c1", "a"), link("y", "a", "b")).valid());
    }

    @Test void disconnectedComponentsAreAllowed() {
        assertTrue(check(List.of(client("c"), api("a"), api("isolated"), db("unused")),
                link("x", "c", "a")).valid());
    }

    @Test void allowsZeroDurationsAndDatabaseQueryKind() {
        assertTrue(check(List.of(client("c"), new ApiV2("a", "API", 0, 0),
                new DatabaseV2("d", "DB", 0)),
                new ConnectionV2("x", "c", "a", 0, rest()),
                new ConnectionV2("y", "a", "d", 0, new DatabaseQueryIntegrationV2(null, null))).valid());
    }

    @Test void catchesDuplicateIdsAndPairsInEitherDirection() {
        var result = check(List.of(client("c"), client("c"), api("a")),
                link("x", "c", "a"), link("x", "a", "c"));
        contains(result, "DUPLICATE_COMPONENT_ID");
        contains(result, "DUPLICATE_CONNECTION_ID");
        contains(result, "DUPLICATE_CONNECTION");
    }

    @Test void rejectsUnknownEndpointsAndInvalidIdentifiers() {
        var result = check(List.of(client(" "), api("a")),
                link("missing", "a", "unknown"),
                link("bad", " ", "a"));
        contains(result, "INVALID_IDENTIFIER");
        contains(result, "UNKNOWN_COMPONENT");
    }

    @Test void rejectsSelfCallsAndUnsupportedDirections() {
        var result = check(List.of(client("c"), api("a"), db("d")),
                link("self", "a", "a"), link("reverse", "a", "c"),
                link("dbout", "d", "a"));
        contains(result, "INVALID_CONNECTION");
    }

    @Test void rejectsIntegrationMismatchesAndMissingIntegration() {
        var result = check(List.of(client("c"), api("a"), db("d")),
                new ConnectionV2("x", "c", "a", 1, new DatabaseQueryIntegrationV2(null, null)),
                new ConnectionV2("y", "a", "d", 1, rest()),
                new ConnectionV2("z", "a", "d", 1, null));
        contains(result, "INVALID_INTEGRATION");
    }

    @Test void rejectsCycleEvenWhenNotInScenario() {
        var result = check(List.of(client("c"), api("entry"), api("outside1"), api("outside2")),
                link("x", "c", "entry"),
                link("a", "outside1", "outside2"),
                link("b", "outside2", "outside1"));
        contains(result, "CYCLIC_DEPENDENCY");
    }

    @Test void rejectsCycleOfThree() {
        var result = check(List.of(api("a"), api("b"), api("c")),
                link("ab", "a", "b"), link("bc", "b", "c"), link("ca", "c", "a"));
        contains(result, "CYCLIC_DEPENDENCY");
    }

    @Test void acceptsLongAcyclicChainWithoutRecursion() {
        List<ComponentV2> components = new ArrayList<>();
        List<ConnectionV2> edges = new ArrayList<>();
        for (int i = 0; i < 900; i++) {
            components.add(api("a" + i));
            if (i > 0) edges.add(link("l" + i, "a" + (i - 1), "a" + i));
        }
        assertTrue(validator.validate(new ArchitectureV2(components, edges)).valid());
    }

    @Test void checksDurationAndUnicodeDisplayNameBoundaries() {
        var valid = check(List.of(new ApiV2("a", "😀".repeat(80), 0, 1_000_000_000L)));
        assertTrue(valid.valid());
        var invalid = check(List.of(new ApiV2("a", "😀".repeat(81), -1, 1_000_000_001L)),
                new ConnectionV2("x", "a", "missing", -5, rest()));
        contains(invalid, "INVALID_DISPLAY_NAME");
        contains(invalid, "INVALID_DURATION");
    }

    @Test void rejectsBlankDisplayNameAndTooLongIdentifiers() {
        var result = check(List.of(new ClientV2("x".repeat(129), "   "), api("a")));
        contains(result, "INVALID_IDENTIFIER");
        contains(result, "INVALID_DISPLAY_NAME");
    }

    @Test void rejectsUnboundedArchitectureSize() {
        List<ComponentV2> components = new ArrayList<>();
        for (int i = 0; i < 1001; i++) components.add(client("c" + i));
        contains(validator.validate(new ArchitectureV2(components, List.of())), "INVALID_TOPOLOGY");
    }

    @Test void reportsIndependentProblemsInStableListOrder() {
        var result = check(List.of(client("a"), api("b"), api("b")),
                link("x", "a", "b"), link("y", "a", "b"));
        assertEquals("DUPLICATE_COMPONENT_ID", result.violations().getFirst().code());
        contains(result, "DUPLICATE_CONNECTION");
    }
}
