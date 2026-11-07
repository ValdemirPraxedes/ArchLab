package br.com.archlab.simulation.core.v2;

/** Finite ETL executor metadata; Behaviors remain in the unified execution plan. */
public record DataProcessorV6(String id, String displayName, ComponentTypeV2 type,
                              int batchSize, int maxBatches, long transformCostPerRecordMs)
        implements ComponentV2 {
    public DataProcessorV6 {
        if (type != ComponentTypeV2.ETL_PIPELINE && type != ComponentTypeV2.DATA_TRANSFORMER)
            throw new IllegalArgumentException("Unsupported data processor type");
    }
}
