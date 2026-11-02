package br.com.archlab.api;
import br.com.archlab.api.v3.ValidationResponseV3;
import br.com.archlab.simulation.core.v3.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class ValidationResponseV3Test {
    @Test void mapsIdsToPublicStringsAndPreservesErrorOrder() {
        var response = ValidationResponseV3.from(new ValidationResultV3(List.of(
                new ViolationV3("A", "/components/0", "invalid", ViolationV3.Severity.ERROR,
                        List.of(new IdentifierV3("api"))),
                new ViolationV3("B", "", "warning", ViolationV3.Severity.WARNING, List.of()))));
        assertFalse(response.valid());
        assertEquals(List.of("A", "B"), response.violations().stream().map(ValidationResponseV3.Violation::code).toList());
        assertEquals(List.of("api"), response.violations().getFirst().relatedIds());
    }
}
