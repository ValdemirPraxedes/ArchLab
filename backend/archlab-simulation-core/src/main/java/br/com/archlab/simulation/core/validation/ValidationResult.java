package br.com.archlab.simulation.core.validation;

import java.util.List;

public record ValidationResult(boolean valid, List<Violation> violations) {
    public ValidationResult {
        violations = List.copyOf(violations);
        if (valid != violations.isEmpty()) {
            throw new IllegalArgumentException("valid must match absence of violations");
        }
    }
}
