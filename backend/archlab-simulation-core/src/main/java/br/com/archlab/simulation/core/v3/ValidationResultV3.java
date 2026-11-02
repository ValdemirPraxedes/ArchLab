package br.com.archlab.simulation.core.v3;
import java.util.List;
/** Preserves validator order and derives validity from error severity. */
public record ValidationResultV3(List<ViolationV3> violations) {
    public ValidationResultV3 { violations = List.copyOf(violations); }
    public boolean valid() {
        return violations.stream().noneMatch(v -> v.severity() == ViolationV3.Severity.ERROR);
    }
}
