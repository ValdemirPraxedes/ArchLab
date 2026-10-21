package br.com.archlab.simulation.core.validation;

import br.com.archlab.simulation.core.domain.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ArchitectureValidatorTest {
    private final ArchitectureValidator validator = new ArchitectureValidator();

    private Architecture architecture() {
        return new Architecture(
                List.of(new ClientComponent("client"), new ApiComponent("api", 30, 20),
                        new DatabaseComponent("db", 150)),
                List.of(new Connection("c1", "client", "api", Protocol.REST, 20),
                        new Connection("c2", "api", "db", Protocol.TCP, 10)));
    }

    private void expect(String code, Architecture a) {
        assertTrue(validator.validate(a).violations().stream().anyMatch(v -> v.code().equals(code)),
                () -> "Expected " + code + ": " + validator.validate(a).violations());
        assertThrows(InvalidArchitectureException.class, () -> validator.requireValid(a));
    }

    @Test void acceptsValidScenarioAndZeroDurations() {
        assertTrue(validator.validate(architecture()).valid());
        assertSame(architecture(), validator.requireValid(architecture()));
        Architecture zero = new Architecture(
                List.of(new ClientComponent("c"), new ApiComponent("a", 0, 0), new DatabaseComponent("d", 0)),
                List.of(new Connection("1", "c", "a", Protocol.SOAP, 0), new Connection("2", "a", "d", Protocol.TCP, 0)));
        assertTrue(validator.validate(zero).valid());
    }

    @Test void rejectsMissingAndExtraEvenIfDisconnected() {
        Architecture valid = architecture();
        expect("MISSING_COMPONENT", new Architecture(valid.components().subList(0, 2), valid.connections()));
        expect("INVALID_COMPONENT_COUNT", new Architecture(
                List.of(valid.components().get(0), valid.components().get(1), valid.components().get(2), new ClientComponent("extra")),
                valid.connections()));
    }

    @Test void rejectsDuplicateComponentAndConnectionIds() {
        Architecture a = architecture();
        expect("DUPLICATE_COMPONENT_ID", new Architecture(
                List.of(new ClientComponent("same"), new ApiComponent("same", 1, 1), new DatabaseComponent("db", 1)), a.connections()));
        expect("DUPLICATE_CONNECTION_ID", new Architecture(a.components(),
                List.of(a.connections().get(0), new Connection("c1", "api", "db", Protocol.TCP, 1))));
    }

    @Test void rejectsDuplicateUnorderedPairAndBackwardsLinks() {
        Architecture a = architecture();
        expect("DUPLICATE_CONNECTION", new Architecture(a.components(),
                List.of(a.connections().get(0), a.connections().get(1),
                        new Connection("c3", "api", "client", Protocol.REST, 1))));
        expect("INVALID_CONNECTION", new Architecture(a.components(),
                List.of(new Connection("c1", "api", "client", Protocol.REST, 1), a.connections().get(1))));
    }

    @Test void rejectsBadProtocolsAndUnrecognizedEndpoints() {
        Architecture a = architecture();
        expect("INVALID_PROTOCOL", new Architecture(a.components(),
                List.of(new Connection("c1", "client", "api", Protocol.TCP, 1), a.connections().get(1))));
        expect("INVALID_PROTOCOL", new Architecture(a.components(),
                List.of(a.connections().get(0), new Connection("c2", "api", "db", Protocol.SOAP, 1))));
        expect("UNKNOWN_COMPONENT", new Architecture(a.components(),
                List.of(new Connection("c1", "client", "missing", Protocol.REST, 1), a.connections().get(1))));
        expect("INVALID_CONNECTION", new Architecture(a.components(),
                List.of(new Connection("c1", "api", "api", Protocol.REST, 1), a.connections().get(1))));
    }

    @Test void rejectsMissingConnectionsAndNegativeDurations() {
        Architecture a = architecture();
        expect("MISSING_CONNECTION", new Architecture(a.components(), List.of(a.connections().get(0))));
        expect("INVALID_DURATION", new Architecture(
                List.of(new ClientComponent("client"), new ApiComponent("api", -1, -2), new DatabaseComponent("db", -3)),
                List.of(new Connection("c1", "client", "api", Protocol.REST, -4), a.connections().get(1))));
    }

    @Test void makesArchitectureAndValidationResultsImmutable() {
        List<Component> components = new ArrayList<>(architecture().components());
        Architecture a = new Architecture(components, architecture().connections());
        components.clear();
        assertEquals(3, a.components().size());
        assertThrows(UnsupportedOperationException.class, () -> a.components().clear());
        assertThrows(UnsupportedOperationException.class, () -> validator.validate(a).violations().clear());
    }
}
