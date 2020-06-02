/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis.matrices;

import ch.sbb.matsim.analysis.skims.CalculateSkimMatrices;
import ch.sbb.matsim.config.variables.SBBModes;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.function.BiPredicate;

/**
 * @author mrieser / SBB
 */
public class CalculateIndicatorMatrices {

    public static void main(String[] args) throws IOException {
        System.setProperty("matsim.preferLocalDtds", "true");

        String zonesShapeFilename = args[0];
        String zonesIdAttributeName = args[1];
        String facilitiesFilename = args[2];
        String networkFilename = args[3];
        String transitScheduleFilename = args[4];
        String eventsFilename = args[5].equals("-") ? null : args[5];
        String outputDirectory = args[6];
        int numberOfPointsPerZone = Integer.valueOf(args[7]);
        int numberOfThreads = Integer.valueOf(args[8]);
        boolean detectTrainLines = Boolean.parseBoolean(args[10]);
        BiPredicate<TransitLine, TransitRoute> trainLinePredictor = detectTrainLines ?
                (line, route) -> route.getTransportMode().equals(SBBModes.PTSubModes.RAIL) :
                (line, route) -> false;

        Map<String, double[]> timesCar = new LinkedHashMap<>();
        Map<String, double[]> timesPt = new LinkedHashMap<>();

        for (int argIdx = 10; argIdx < args.length; argIdx++) {
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
        Random r = new Random(20180404L);

        CalculateSkimMatrices skims = new CalculateSkimMatrices(zonesShapeFilename, zonesIdAttributeName, outputDirectory, numberOfThreads);
        skims.calculateSamplingPointsPerZoneFromFacilities(facilitiesFilename, numberOfPointsPerZone, r, f -> {
            double weight = 2; // default for households
            String fte = (String) f.getAttributes().getAttribute("fte");
            if (fte != null) {
                weight = Double.parseDouble(fte);
            }
            return weight;
        });

        for (Map.Entry<String, double[]> e : timesCar.entrySet()) {
            String prefix = e.getKey();
            double[] times = e.getValue();
            skims.calculateNetworkMatrices(networkFilename, eventsFilename, times, config, prefix, l -> l.getAttributes().getAttribute("accessControlled").toString().equals("0"));
        }

        for (Map.Entry<String, double[]> e : timesPt.entrySet()) {
            String prefix = e.getKey();
            double[] times = e.getValue();
            skims.calculatePTMatrices(networkFilename, transitScheduleFilename, times[0], times[1], config, prefix, trainLinePredictor);
        }

        skims.calculateBeelineMatrix();
    }

}