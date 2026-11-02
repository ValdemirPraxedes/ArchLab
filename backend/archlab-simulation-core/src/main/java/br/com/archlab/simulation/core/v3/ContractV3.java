package br.com.archlab.simulation.core.v3;
/** Schema version is independent of the application's release version. */
public final class ContractV3 {
    public static final String SCHEMA_VERSION = "3";
    public static final String API_PATH = "/api/v3";
    private ContractV3() { }
    public static void requireSchemaVersion(String version) {
        if (!SCHEMA_VERSION.equals(version)) throw new IllegalArgumentException("Unsupported schema version");
    }
}
