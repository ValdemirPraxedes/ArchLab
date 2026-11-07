package br.com.archlab.simulation.core.v2;

/** Configuration of a finite, per-execution logical data resource. */
public record DataResourceV6(String id, String displayName, ComponentTypeV2 type,
                             int initialRecordCount, int averageRecordBytes,
                             long readLatencyMs, long writeLatencyMs,
                             int maxConcurrentOperations, int connectionPoolSize,
                             int queueCapacity) implements ComponentV2 {
    public DataResourceV6 {
        if (type != ComponentTypeV2.RELATIONAL_DATABASE
                && type != ComponentTypeV2.DOCUMENT_DATABASE
                && type != ComponentTypeV2.KEY_VALUE_STORE)
            throw new IllegalArgumentException("Unsupported data resource type");
    }
}
