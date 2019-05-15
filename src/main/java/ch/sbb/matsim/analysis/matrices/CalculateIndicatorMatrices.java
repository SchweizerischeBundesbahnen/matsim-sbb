/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis.matrices;

import ch.sbb.matsim.analysis.skims.CalculateSkimMatrices;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.core.utils.misc.Time;

import java.io.IOException;
import java.util.Random;
import java.util.Set;

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
        String eventsFilename = args[5];
        String outputDirectory = args[6];
        int numberOfPointsPerZone = Integer.valueOf(args[7]);
        int numberOfThreads = Integer.valueOf(args[8]);
        String[] timesCarStr = args[9].split(";");
        String[] timesPtStr = args[10].split(";");
        Set<String> modes = CollectionUtils.stringToSet(args[11]);

        double[] timesCar = new double[timesCarStr.length];
        for (int i = 0; i < timesCarStr.length; i++)
            timesCar[i] = Time.parseTime(timesCarStr[i]);

        double[] timesPt = new double[timesPtStr.length];
        for (int i = 0; i < timesPtStr.length; i++)
            timesPt[i] = Time.parseTime(timesPtStr[i]);

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

        if (modes.contains(TransportMode.car)) {
            skims.calculateNetworkMatrices(networkFilename, eventsFilename, timesCar, config, l -> true);
        }

        if (modes.contains(TransportMode.pt)) {
            skims.calculatePTMatrices(transitScheduleFilename, timesPt[0], timesPt[1], config, (line, route) -> "SBB_Simba.CH_2016".equals(route.getAttributes().getAttribute("01_Datenherkunft")));
        }

        skims.calculateBeelineMatrix();
    }

}