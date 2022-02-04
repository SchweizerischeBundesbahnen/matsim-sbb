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

package ch.sbb.matsim.preparation.casestudies;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;

public class GenerateMiniConfig {

    public static void main(String[] args) {
        String inputConfig = args[0];
        String changeEventsFile = args[1];
        double sampleSize = Double.parseDouble(args[2]);
        String outputFile = args[3];

        Config config = ConfigUtils.loadConfig(inputConfig, RunSBB.getSbbDefaultConfigGroups());
        config.controler().setMobsim("qsim");
        config.network().setTimeVariantNetwork(true);
        config.network().setChangeEventsInputFile(changeEventsFile);
        PostProcessingConfigGroup ppc = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class);
        ppc.setSimulationSampleSize(sampleSize);

        new ConfigWriter(config).write(outputFile);
    }

}
