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
        // --- Processed Car Matrices ---
        CAR_NET_DISTANCE(null),
        CAR_TRAVEL_TIMES(null),

        // --- Processed PT Matrices ---
        PT_TRAVEL_TIMES_TOTAL(null), // The main travel time, symmetrized
        PT_TRAVEL_TIMES_TRAIN(null), // Derived train-only travel time
        PT_TRAVEL_TIMES_LOCAL(null), // Derived non-train travel time
        PT_ACCESS_TIME(null),        // Symmetrized and averaged
        PT_EGRESS_TIME(null),        // Symmetrized and averaged
        PT_TRANSFERS(null),          // Symmetrized
        PT_FREQUENCY(null);          // Symmetrized


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