package br.com.archlab.simulation.core.v3;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class FoundationV3Test {
    @Test void identifiersFollowContractWithoutTrimming() {
        assertEquals(" a ", new IdentifierV3(" a ").value());
        assertEquals(128, new IdentifierV3("a".repeat(128)).value().length());
        assertDoesNotThrow(() -> new IdentifierV3("😀".repeat(128)));
        for (String invalid : List.of("", " ", "\t\n\r", "\u00A0", "\uFEFF", "\u2028", "a".repeat(129)))
            assertThrows(IllegalArgumentException.class, () -> new IdentifierV3(invalid));
        assertDoesNotThrow(() -> new IdentifierV3("\u0085"));
        assertThrows(NullPointerException.class, () -> new IdentifierV3(null));
    }
    @Test void durationsRejectNegativeValuesAndOverflow() {
        assertEquals(new DurationMsV3(15), new DurationMsV3(10).plus(new DurationMsV3(5)));
        assertEquals(DurationMsV3.ZERO, new DurationMsV3(0));
        assertThrows(IllegalArgumentException.class, () -> new DurationMsV3(-1));
        assertThrows(ArithmeticException.class, () -> new DurationMsV3(Long.MAX_VALUE).plus(new DurationMsV3(1)));
    }
    @Test void warningsDoNotInvalidateAndListsAreDefensivelyCopied() {
        var ids = new ArrayList<>(List.of(new IdentifierV3("api")));
        var warning = new ViolationV3("W", "/components/0", "warning", ViolationV3.Severity.WARNING, ids);
        ids.clear();
        assertEquals(1, warning.relatedIds().size());
        var entries = new ArrayList<>(List.of(warning));
        var result = new ValidationResultV3(entries);
        entries.clear();
        assertTrue(result.valid());
        assertEquals(List.of(warning), result.violations());
        assertThrows(UnsupportedOperationException.class, () -> result.violations().clear());
        assertFalse(new ValidationResultV3(List.of(new ViolationV3("E", "", "error",
                ViolationV3.Severity.ERROR, List.of()))).valid());
    }
    @Test void unsupportedVersionsAndMalformedErrorsFailAtBoundary() {
        assertDoesNotThrow(() -> ContractV3.requireSchemaVersion("3"));
        for (String version : List.of("1", "2", "0.3.0", ""))
            assertThrows(IllegalArgumentException.class, () -> ContractV3.requireSchemaVersion(version));
        assertThrows(IllegalArgumentException.class, () -> ContractV3.requireSchemaVersion(null));
        assertThrows(IllegalArgumentException.class, () -> new ViolationV3("E", "components", "error",
                ViolationV3.Severity.ERROR, List.of()));
    }
}
