/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis.skims;

import ch.sbb.matsim.analysis.skims.PTSkimMatrices.PtIndicators;
import ch.sbb.matsim.config.variables.SBBModes;
import io.jhdf.HdfFile;
import io.jhdf.WritableHdfFile;
import io.jhdf.api.WritableGroup;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

/**
 * @author mrieser / SBB
 */
public class CalculateIndicatorOMXMatrices {

    static final Logger log = LogManager.getLogger(CalculateIndicatorOMXMatrices.class);

    public static void main(String[] args) throws IOException {
        System.setProperty("matsim.preferLocalDtds", "true");
        String coordinatesFilename = args[0];
        String networkFilename = args[1];
        String transitScheduleFilename = args[2];
        String eventsFilename = args[3].equals("-") ? null : args[3];
        String outputDirectory = args[4];
        int numberOfThreads = Integer.valueOf(args[5]);
        boolean detectTrainLines = Boolean.parseBoolean(args[6]);

        System.out.println("Detecting train lines: " + detectTrainLines);
        BiPredicate<TransitLine, TransitRoute> trainLinePredictor = detectTrainLines ?
                (line, route) -> route.getTransportMode().equals(SBBModes.PTSubModes.RAIL) :
                (line, route) -> false;

        Map<String, double[]> timesCar = new LinkedHashMap<>();
        Map<String, double[]> timesPt = new LinkedHashMap<>();

        for (int argIdx = 7; argIdx < args.length; argIdx++) {
            String arg = args[argIdx];
            String mode = null;
            String data = null;
            if (arg.startsWith("car=")) {
                mode = "car";
                data = arg.substring(4);
            }
            if (arg.startsWith("pt=")) {
                mode = "pt";
                data = arg.substring(3);
            }
            if (data != null) {
                String[] parts = data.split(";");
                String prefix = parts[0];
                double[] times = new double[parts.length - 1];
                for (int timeIndex = 0; timeIndex < times.length; timeIndex++) {
                    times[timeIndex] = Time.parseTime(parts[timeIndex + 1]);
                }
                if (mode.equals("car")) {
                    timesCar.put(prefix, times);
                }
                if (mode.equals("pt")) {
                    timesPt.put(prefix, times);
                }
            }
        }

        Config config = ConfigUtils.createConfig();

        CalculateSkimMatrices skims = new CalculateSkimMatrices(outputDirectory, numberOfThreads);
        skims.loadSamplingPointsFromFile(coordinatesFilename);
        TreeSet<Integer> zones = skims.getCoordsPerZone().keySet().stream().map(Integer::parseInt).collect(Collectors.toCollection(TreeSet::new));
        int[] lookupzones = zones.stream().mapToInt(Integer::intValue).toArray();

        if (!timesCar.isEmpty()) {
            try (WritableHdfFile hdfFile = HdfFile.write(Paths.get(outputDirectory + "/car_skims.omx"))) {
                WritableGroup lookupGroup = hdfFile.putGroup("lookup");
                lookupGroup.putDataset("NO", lookupzones);
                for (Map.Entry<String, double[]> e : timesCar.entrySet()) {
                    String prefix = e.getKey();
                    double[] times = e.getValue();
                    var networkMatrices = skims
                            .prepareAndCalculateNetworkMatrices(networkFilename, eventsFilename, times, config, l -> String.valueOf(l.getAttributes().getAttribute("accessControlled")).equals("0"));
                    exportNetworkMatrices(hdfFile, prefix, networkMatrices, lookupzones);
                }
            }

        }

        for (Map.Entry<String, double[]> e : timesPt.entrySet()) {
            String prefix = e.getKey();
            double[] times = e.getValue();

            PTSkimMatrices.PtIndicators<String> matrices = skims.calculatePTMatrices(networkFilename, transitScheduleFilename, times[0], times[1], config, trainLinePredictor, new DiameterBasedCoordCondenser());
            exportPtSkimMatrices(outputDirectory + "/" + prefix + "pt_skims.omx", matrices, lookupzones);
        }

        var beelineMatrix = skims.calculateBeelineMatrix();
        exportBeelineMatrix(outputDirectory + "/beeline_distance_matrix.omx", beelineMatrix, lookupzones);
    }

    private static void exportBeelineMatrix(String filename, FloatMatrix<String> beelineMatrix, int[] lookupzones) {
        log.info("Writing Beeline Distance OMX matrix");
        try (WritableHdfFile hdfFile = HdfFile.write(Paths.get(filename))) {
            WritableGroup lookupGroup = hdfFile.putGroup("lookup");
            lookupGroup.putDataset("NO", lookupzones);
            WritableGroup data = hdfFile.putGroup("data");
            data.putDataset("beeline_distances", getFloats(lookupzones, beelineMatrix));
        }
        log.info("done xxx");
    }

    private static void exportNetworkMatrices(WritableHdfFile omxFile, String prefix, NetworkSkimMatrices.NetworkIndicators<String> networkMatrices, int[] lookupzones) {
        log.info("Adding Network Traveltime OMX matrix for " + prefix);
        WritableGroup data = (WritableGroup) omxFile.getChild("data");
        if (data == null) {
            data = omxFile.putGroup("data");
        }
        data.putDataset(prefix + "distances", getFloats(lookupzones, networkMatrices.distanceMatrix));
        data.putDataset(prefix + "travel_times", getFloats(lookupzones, networkMatrices.travelTimeMatrix));
        log.info("done xxx");
    }

    private static void exportPtSkimMatrices(String filename, PtIndicators<String> matrices, int[] lookupzones) {
        try (WritableHdfFile hdfFile = HdfFile.write(Paths.get(filename))) {
            WritableGroup lookupGroup = hdfFile.putGroup("lookup");
            lookupGroup.putDataset("NO", lookupzones);
            WritableGroup data = hdfFile.putGroup("data");
            data.putDataset("access_times", getFloats(lookupzones, matrices.accessTimeMatrix));
            data.putDataset("frequencies", getFloats(lookupzones, matrices.frequencyMatrix));
            data.putDataset("distances", getFloats(lookupzones, matrices.distanceMatrix));
            data.putDataset("adaption_times", getFloats(lookupzones, matrices.adaptionTimeMatrix));
            data.putDataset("data_counts", getFloats(lookupzones, matrices.dataCountMatrix));
            data.putDataset("egress_times", getFloats(lookupzones, matrices.egressTimeMatrix));
            data.putDataset("transfer_counts", getFloats(lookupzones, matrices.transferCountMatrix));
            data.putDataset("travel_times", getFloats(lookupzones, matrices.travelTimeMatrix));
            data.putDataset("train_distance_shares", getFloats(lookupzones, matrices.trainDistanceShareMatrix));
            data.putDataset("train_traveltime_shares", getFloats(lookupzones, matrices.trainTravelTimeShareMatrix));

        }

        log.info("done xxx");

    }

    private static float[][] getFloats(int[] lookupzones, FloatMatrix<String> matrix) {
        float[][] result = new float[lookupzones.length][lookupzones.length];
        for (int i = 0; i < lookupzones.length; i++) {
            String fromZone = Integer.toString(lookupzones[i]);
            for (int j = 0; j < lookupzones.length; j++) {
                String toZone = Integer.toString(lookupzones[j]);
                result[i][j] = matrix.get(fromZone, toZone);
            }
        }
        return result;
    }

}