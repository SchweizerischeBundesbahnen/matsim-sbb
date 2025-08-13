package ch.sbb.matsim.plans.matrices;

/**
 * Defines a contract for enums that represent matrices, linking them to their file key.
 */
interface MatrixDefinition {
    String getFileKey();

    String name(); // Every enum has this implicitly
}

public class MatrixNames {

    public enum KalifMatrices implements MatrixDefinition {
        KALIF_EDUCATION_PRIM("kalif_education_prim"),
        KALIF_EDUCATION_SEC("kalif_education_sec"),
        KALIF_WORK("kalif_work"),
        KALIF_WORK_CB("kalif_work_cb"),
        VA_IMPEDANCES("va_impedances"),
        KALIF_SECONDARY_ACTS("kalif_secondary_acts");

        private final String fileKey;

        KalifMatrices(String fileKey) {
            this.fileKey = fileKey;
        }

        @Override
        public String getFileKey() {
            return this.fileKey;
        }
    }

    public enum TravelMetricsMatrices implements MatrixDefinition {
        // --- Processed Matrices (don't have a single direct file key) ---
        CAR_NET_DISTANCE(null),
        CAR_TRAVEL_TIMES(null),

        // --- Matrices with a direct 1-to-1 mapping from file ---
        BEELINE_DISTANCES("beeline_distances"),
        ACCESS_TIMES("access_times"),
        EGRESS_TIMES("egress_times"),
        FREQUENCIES("frequencies"),
        TRAIN_TRAVELTIME_SHARES("train_traveltime_shares"),
        TRANSFER_COUNTS("transfer_counts"),
        TRAVEL_TIMES("travel_times");

        private final String fileKey;

        TravelMetricsMatrices(String fileKey) {
            this.fileKey = fileKey;
        }

        @Override
        public String getFileKey() {
            if (this.fileKey == null) {
                throw new UnsupportedOperationException("Matrix " + this.name() + " is a processed matrix and has no direct file key.");
            }
            return this.fileKey;
        }
    }
}