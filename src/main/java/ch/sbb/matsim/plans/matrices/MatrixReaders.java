package ch.sbb.matsim.plans.matrices;

import ch.sbb.matsim.analysis.skims.FloatMatrix;
import ch.sbb.matsim.utils.matrix.OmxUtils;
import ch.sbb.matsim.zones.Zone;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;

import java.util.Map;
import java.util.Objects;

public class MatrixReaders {

    private static final Logger logger = LogManager.getLogger(MatrixReaders.class);

    // Define raw input matrix names as constants to avoid magic strings
    private static final String AM_CAR_DISTANCES = "am_distances";
    private static final String PM_CAR_DISTANCES = "pm_distances";
    private static final String AM_CAR_TRAVEL_TIMES = "am_travel_times";
    private static final String PM_CAR_TRAVEL_TIMES = "pm_travel_times";

    private static final String PT_ACCESS_TIMES = "access_times";
    private static final String PT_EGRESS_TIMES = "egress_times";
    private static final String PT_TRAVEL_TIMES = "travel_times";
    private static final String PT_TRAIN_SHARE = "train_traveltime_shares";
    private static final String PT_FREQUENCIES = "frequencies";
    private static final String PT_TRANSFERS = "transfer_counts";

    /**
     * Reads and processes car skim matrices, which involves merging and symmetrizing.
     */
    public static void readAndProcessCarSkims(String filePath, Matrices matrices) {
        logger.info("Reading and processing car skims from file: {}", filePath);
        Map<String, FloatMatrix<Id<Zone>>> skimDict = OmxUtils.readOmxMatrices(filePath);

        // Get raw matrices using constants
        FloatMatrix<Id<Zone>> amDistances = Objects.requireNonNull(skimDict.get(AM_CAR_DISTANCES), "Matrix not found: " + AM_CAR_DISTANCES);
        FloatMatrix<Id<Zone>> pmDistances = Objects.requireNonNull(skimDict.get(PM_CAR_DISTANCES), "Matrix not found: " + PM_CAR_DISTANCES);
        FloatMatrix<Id<Zone>> amTravelTimes = Objects.requireNonNull(skimDict.get(AM_CAR_TRAVEL_TIMES), "Matrix not found: " + AM_CAR_TRAVEL_TIMES);
        FloatMatrix<Id<Zone>> pmTravelTimes = Objects.requireNonNull(skimDict.get(PM_CAR_TRAVEL_TIMES), "Matrix not found: " + PM_CAR_TRAVEL_TIMES);

        logger.info("Processing car travel times...");
        FloatMatrix<Id<Zone>> mergedTravelTimes = MatrixUtils.mergeByMaximum(amTravelTimes, pmTravelTimes);
        FloatMatrix<Id<Zone>> symmetricTravelTimes = MatrixUtils.getSymmetricMatrix(mergedTravelTimes);
        matrices.addMatrix(MatrixNames.TravelMetricsMatrices.CAR_TRAVEL_TIMES.name().toLowerCase(), symmetricTravelTimes);

        logger.info("Processing car network distances...");
        FloatMatrix<Id<Zone>> mergedDistances = MatrixUtils.mergeByMaximum(amDistances, pmDistances);
        FloatMatrix<Id<Zone>> distancesWithMin = MatrixUtils.applyLowerBound(mergedDistances, 300.0f);
        FloatMatrix<Id<Zone>> symmetricDistances = MatrixUtils.getSymmetricMatrix(distancesWithMin);
        matrices.addMatrix(MatrixNames.TravelMetricsMatrices.CAR_NET_DISTANCE.name().toLowerCase(), symmetricDistances);

        logger.info("Finished processing car skims.");
    }

    public static void readAndProcessPTSkims(String filePath, Matrices matrices) {
        logger.info("Reading and processing PT skims from file: {}", filePath);
        Map<String, FloatMatrix<Id<Zone>>> skimDict = OmxUtils.readOmxMatrices(filePath);

        // 1. Get all raw matrices
        FloatMatrix<Id<Zone>> rawAccess = Objects.requireNonNull(skimDict.get(PT_ACCESS_TIMES), "Matrix not found: " + PT_ACCESS_TIMES);
        FloatMatrix<Id<Zone>> rawEgress = Objects.requireNonNull(skimDict.get(PT_EGRESS_TIMES), "Matrix not found: " + PT_EGRESS_TIMES);
        FloatMatrix<Id<Zone>> rawTravelTimes = Objects.requireNonNull(skimDict.get(PT_TRAVEL_TIMES), "Matrix not found: " + PT_TRAVEL_TIMES);
        FloatMatrix<Id<Zone>> rawTrainShare = Objects.requireNonNull(skimDict.get(PT_TRAIN_SHARE), "Matrix not found: " + PT_TRAIN_SHARE);
        FloatMatrix<Id<Zone>> rawFrequencies = Objects.requireNonNull(skimDict.get(PT_FREQUENCIES), "Matrix not found: " + PT_FREQUENCIES);
        FloatMatrix<Id<Zone>> rawTransfers = Objects.requireNonNull(skimDict.get(PT_TRANSFERS), "Matrix not found: " + PT_TRANSFERS);

        // 2. Process Transfers, Frequencies, and Total Travel Time (simple symmetrization)
        logger.info("Processing PT transfers, frequencies, and total travel time...");
        matrices.addMatrix(MatrixNames.TravelMetricsMatrices.PT_TRANSFERS.name().toLowerCase(), MatrixUtils.getSymmetricMatrix(rawTransfers));
        matrices.addMatrix(MatrixNames.TravelMetricsMatrices.PT_FREQUENCY.name().toLowerCase(), MatrixUtils.getSymmetricMatrix(rawFrequencies));
        matrices.addMatrix(MatrixNames.TravelMetricsMatrices.PT_TRAVEL_TIMES_TOTAL.name().toLowerCase(), MatrixUtils.getSymmetricMatrix(rawTravelTimes));

        // 3. Process Train vs. Local Travel Times
        logger.info("Processing train and local PT travel times...");
        FloatMatrix<Id<Zone>> cleanedTrainShare = MatrixUtils.transform(rawTrainShare, v ->
                (!Float.isFinite(v) || v < 0 || v > 1) ? 0.0f : v // A share must be between 0 and 1
        );

        FloatMatrix<Id<Zone>> trainTtRaw = MatrixUtils.merge(rawTravelTimes, cleanedTrainShare, (tt, share) -> isInvalidTime(tt) ? MatrixUtils.DEFAULT_SKIM_ENTRY: tt * share);
        matrices.addMatrix(MatrixNames.TravelMetricsMatrices.PT_TRAVEL_TIMES_TRAIN.name().toLowerCase(), MatrixUtils.getSymmetricMatrix(trainTtRaw));
        FloatMatrix<Id<Zone>> nonTrainShare = MatrixUtils.transform(cleanedTrainShare, v -> 1.0f - v);
        FloatMatrix<Id<Zone>> localTtRaw = MatrixUtils.merge(rawTravelTimes, nonTrainShare, (tt, share) -> isInvalidTime(tt) ? MatrixUtils.DEFAULT_SKIM_ENTRY: tt * share);
        matrices.addMatrix(MatrixNames.TravelMetricsMatrices.PT_TRAVEL_TIMES_LOCAL.name().toLowerCase(), MatrixUtils.getSymmetricMatrix(localTtRaw));

        // 4. Process Access and Egress Times
        logger.info("Processing PT access and egress times...");
        // Transpose using the utility class
        FloatMatrix<Id<Zone>> egressTransposed = MatrixUtils.transpose(rawEgress);
        FloatMatrix<Id<Zone>> accessTransposed = MatrixUtils.transpose(rawAccess);

        FloatMatrix<Id<Zone>> accessIntermediate = MatrixUtils.mergeByAverage(rawAccess, egressTransposed);
        FloatMatrix<Id<Zone>> egressIntermediate = MatrixUtils.mergeByAverage(rawEgress, accessTransposed);

        matrices.addMatrix(MatrixNames.TravelMetricsMatrices.PT_ACCESS_TIME.name().toLowerCase(), MatrixUtils.getSymmetricMatrix(accessIntermediate));
        matrices.addMatrix(MatrixNames.TravelMetricsMatrices.PT_EGRESS_TIME.name().toLowerCase(), MatrixUtils.getSymmetricMatrix(egressIntermediate));

        logger.info("Finished processing PT skims.");
    }

    /**
     * Reads all Kalif matrices, which are direct 1-to-1 mappings from the file.
     */
    public static void readKalifMatrices(String filePath, Matrices matrices) {
        logger.info("Reading Kalif matrices from file: {}", filePath);
        Map<String, FloatMatrix<Id<Zone>>> skimDict = OmxUtils.readOmxMatrices(filePath);
        addSimpleMatrices(skimDict, matrices, MatrixNames.KalifMatrices.values());
        logger.info("Finished reading Kalif matrices.");
    }

    /**
     * A generic helper to read matrices that have a direct 1-to-1 mapping from a file key.
     * By default, the matrix is added with a lowercase name of the enum value.
     * @param source      The map of all matrices read from the OMX file.
     * @param destination The Matrices container to add the results to.
     * @param definitions An array of enum values that implement MatrixDefinition.
     */
    private static void addSimpleMatrices(Map<String, FloatMatrix<Id<Zone>>> source, Matrices destination, MatrixDefinition[] definitions) {
        for (MatrixDefinition def : definitions) {
            FloatMatrix<Id<Zone>> matrix = source.get(def.getFileKey());
            if (matrix != null) {
                destination.addMatrix(def.name().toLowerCase(), matrix);
                logger.debug("Added matrix from file key '{}' as '{}'", def.getFileKey(), def.name().toLowerCase());
            } else {

                throw new IllegalStateException("Required matrix with key '" + def.getFileKey() + "' not found in the file.");
            }
        }
    }

    private static boolean isInvalidTime(float tt) {
        return !Float.isFinite(tt) || tt >= MatrixUtils.DEFAULT_SKIM_ENTRY;
    }

}