/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis.matrices;

import ch.sbb.matsim.analysis.skims.CalculateSkimMatrices;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;

import java.io.IOException;
import java.util.*;
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
        String trainLinePredStr = args[9].equals("-") ? null : args[9]; // list of ; separated "or" conditions
        BiPredicate<TransitLine, TransitRoute> trainLinePredictor = buildTrainLinePredictor(trainLinePredStr);

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

    private static BiPredicate<TransitLine, TransitRoute> buildTrainLinePredictor(String str) {
        if (str == null) {
            return (line, route) -> false;
        }

        String[] conditionStrings = str.split(";");
        Condition[] conditions = new Condition[conditionStrings.length];
        for (int i = 0; i < conditionStrings.length; i++) {
            String[] parts = conditionStrings[i].split(",");
            String attribute = parts[0];
            String method = parts[1];
            String value = parts[2];
            ConditionType type;
            if (method.equals("equals")) {
                type = ConditionType.EQUALS;
            } else if (method.equals("contains")) {
                type = ConditionType.CONTAINS;
            } else {
                throw new UnsupportedOperationException("Unsupported condition type: " + method);
            }
            conditions[i] = new Condition(attribute, type, value);
        }

        return (line, route) -> {
           for (Condition c : conditions) {
               if (c.testCondition(route)) {
                   return true;
               }
           }
           return false;
        };
    }

    private enum ConditionType { EQUALS, CONTAINS }

    private static class Condition {
        final String attribute;
        final ConditionType type;
        final String value;

        Condition(String attribute, ConditionType type, String value) {
            this.attribute = attribute;
            this.type = type;
            this.value = value;
        }

        boolean testCondition(TransitRoute route) {
            Object attributeValueObj = route.getAttributes().getAttribute(this.attribute);
            String attributeValue = attributeValueObj == null ? "" : attributeValueObj.toString();
            switch (this.type) {
                case EQUALS:
                    return attributeValue.equals(this.value);
                case CONTAINS:
                    return attributeValue.contains(this.value);
            }
            throw new RuntimeException("Unsupported condition type " + this.type);
        }
    }
}