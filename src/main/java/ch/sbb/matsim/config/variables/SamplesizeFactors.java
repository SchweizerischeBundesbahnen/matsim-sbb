/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2020 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package ch.sbb.matsim.config.variables;

import ch.sbb.matsim.config.PostProcessingConfigGroup;
import org.apache.logging.log4j.LogManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;

import java.util.HashMap;
import java.util.Map;

public class SamplesizeFactors {

    private final static Map<Double, Double> qsimStorageCapacityFactor;
    private final static Map<Double, Double> hermesStorageCapacityFactor;
    private final static Map<Double, Double> hermesFlowCapacityFactor;

    static {
        qsimStorageCapacityFactor = new HashMap<>();
        qsimStorageCapacityFactor.put(0.25, 0.60);
        qsimStorageCapacityFactor.put(0.10, 0.40);
        qsimStorageCapacityFactor.put(0.05, 0.20);
        qsimStorageCapacityFactor.put(0.01, 0.05);

        hermesStorageCapacityFactor = new HashMap<>();
        hermesStorageCapacityFactor.put(0.10, 0.20);
        hermesStorageCapacityFactor.put(0.05, 0.15);

        hermesFlowCapacityFactor = new HashMap<>();
        hermesFlowCapacityFactor.put(0.10, 0.1075);
        hermesFlowCapacityFactor.put(0.05, 0.055);

    }

    public static double getQsimStorageCapacityFactor(double sampleSize) {
        Double f = qsimStorageCapacityFactor.get(sampleSize);
        return f != null ? f : sampleSize;
    }

    public static double getHermesStorageCapacityFactor(double sampleSize) {
        Double f = hermesStorageCapacityFactor.get(sampleSize);
        return f != null ? f : sampleSize;
    }

    public static double getHermesFlowCapacityFactor(double sampleSize) {
        Double f = hermesFlowCapacityFactor.get(sampleSize);
        return f != null ? f : sampleSize;
    }

    public static void setFlowAndStorageCapacities(Config config) {
        var log = LogManager.getLogger(SamplesizeFactors.class);
        double sampleSize = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class).getSimulationSampleSize();
        log.info("Setting flow and storage capacity factors according to sample size: " + sampleSize);
        config.qsim().setFlowCapFactor(sampleSize);
        final double qsimStorageCapacityFactor = getQsimStorageCapacityFactor(sampleSize);
        config.qsim().setStorageCapFactor(qsimStorageCapacityFactor);
        final double hermesFlowCapacityFactor = getHermesFlowCapacityFactor(sampleSize);
        config.hermes().setFlowCapacityFactor(hermesFlowCapacityFactor);
        final double hermesStorageCapacityFactor = getHermesStorageCapacityFactor(sampleSize);
        config.hermes().setStorageCapacityFactor(hermesStorageCapacityFactor);
        log.info("QSIM flowCapacity: " + sampleSize);
        log.info("QSIM storageCapacity: " + qsimStorageCapacityFactor);
        log.info("HERMES flowCapacity: " + hermesFlowCapacityFactor);
        log.info("HERMES storageCapacity: " + hermesStorageCapacityFactor);

    }
}
