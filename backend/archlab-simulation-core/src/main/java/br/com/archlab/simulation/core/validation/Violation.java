package br.com.archlab.simulation.core.validation;

import java.util.Map;
import java.util.Objects;

public record Violation(String code, String path, String message, Map<String, Object> details) {
    public Violation {
        Objects.requireNonNull(code);
        Objects.requireNonNull(path);
        Objects.requireNonNull(message);
        details = Map.copyOf(Objects.requireNonNull(details));
    }
    public Violation(String code, String path, String message) {
        this(code, path, message, Map.of());
    }
}
