package org.simulation.simulation;

public class ConsistencyModel {

    public enum DataClass {
        CRITICAL,
        READ_MOSTLY,
        EVENTUAL
    }

    public enum ConsistencyMode {
        STRONG_QUORUM,
        BOUNDED_STALENESS,
        EVENTUAL
    }

    public record SyncResult(ConsistencyMode mode, double syncCost) {}

    public static SyncResult compute(DataClass dataClass, double writeRate, double latencyInterCloud, double dataSizeMB) {
        return switch (dataClass) {
            case CRITICAL -> {
                double syncCost = writeRate * (latencyInterCloud / 1000.0) * (dataSizeMB / 1024.0);
                yield new SyncResult(ConsistencyMode.STRONG_QUORUM, syncCost);
            }
            case READ_MOSTLY -> {
                double batchFactor = 0.1;
                double syncCost = writeRate * (latencyInterCloud / 1000.0) * (dataSizeMB / 1024.0) * batchFactor;
                yield new SyncResult(ConsistencyMode.BOUNDED_STALENESS, syncCost);
            }
            case EVENTUAL -> {
                double syncCost = writeRate * (dataSizeMB / 1024.0) * 0.001;
                yield new SyncResult(ConsistencyMode.EVENTUAL, syncCost);
            }
        };
    }

    public static DataClass inferClass(String relationName, double writeRate) {
        if (writeRate > 0.3) return DataClass.CRITICAL;
        if (writeRate > 0.05) return DataClass.READ_MOSTLY;
        return DataClass.EVENTUAL;
    }
}