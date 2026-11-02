package br.com.archlab.api.v3;
import br.com.archlab.simulation.core.v3.ValidationResultV3;
import br.com.archlab.simulation.core.v3.ViolationV3;
import java.util.List;
/** Unwraps domain value objects at the HTTP representation boundary. */
public record ValidationResponseV3(boolean valid, List<Violation> violations) {
    public ValidationResponseV3 { violations = List.copyOf(violations); }
    public static ValidationResponseV3 from(ValidationResultV3 result) {
        return new ValidationResponseV3(result.valid(), result.violations().stream()
                .map(v -> new Violation(v.code(), v.path(), v.message(), v.severity(),
                        v.relatedIds().stream().map(id -> id.value()).toList())).toList());
    }
    public record Violation(String code, String path, String message, ViolationV3.Severity severity,
                            List<String> relatedIds) {
        public Violation { relatedIds = List.copyOf(relatedIds); }
    }
}
