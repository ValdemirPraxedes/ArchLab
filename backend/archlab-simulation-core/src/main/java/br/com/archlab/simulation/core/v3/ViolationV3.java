package br.com.archlab.simulation.core.v3;
import java.util.List;
import java.util.Objects;
/** Stable domain error independent of HTTP or JSON libraries. */
public record ViolationV3(String code, String path, String message, Severity severity,
                          List<IdentifierV3> relatedIds) {
    public enum Severity { ERROR, WARNING }
    public ViolationV3 {
        if (code == null || code.isEmpty()) throw new IllegalArgumentException("code is required");
        if (message == null || message.isEmpty()) throw new IllegalArgumentException("message is required");
        if (path == null || (!path.isEmpty() && !path.startsWith("/")))
            throw new IllegalArgumentException("path must be a JSON pointer");
        Objects.requireNonNull(severity, "severity");
        relatedIds = List.copyOf(relatedIds);
    }
}
