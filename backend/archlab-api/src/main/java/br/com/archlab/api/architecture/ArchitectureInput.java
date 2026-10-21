package br.com.archlab.api.architecture;

import java.util.List;

/** Input DTOs stay within the HTTP module; the core never imports them. */
public record ArchitectureInput(List<ComponentInput> components, List<ConnectionInput> connections) {
    public sealed interface ComponentInput permits ClientInput, ApiInput, DatabaseInput {
        String id();
    }
    public record ClientInput(String id) implements ComponentInput {}
    public record ApiInput(String id, long preProcessingMs, long postProcessingMs) implements ComponentInput {}
    public record DatabaseInput(String id, long processingMs) implements ComponentInput {}
    public record ConnectionInput(String id, String sourceId, String targetId, String protocol, long latencyMs) {}
}
