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
import ch.sbb.matsim.config.SBBSupplyConfigGroup;
import ch.sbb.matsim.config.ZonesListConfigGroup;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.PlansConfigGroup;

import java.nio.file.Paths;

public class GenerateMiniConfig {

    public static void main(String[] args) {
        String inputConfig = args[0];
        String changeEventsFile = args[1];
        double sampleSize = Double.parseDouble(args[2]);
        String outputFile = args[3];
        String plansFile = args[4];

        Config config = ConfigUtils.loadConfig(inputConfig, RunSBB.getSbbDefaultConfigGroups());
        config.controler().setMobsim("qsim");
        config.network().setTimeVariantNetwork(true);
        config.network().setChangeEventsInputFile(changeEventsFile);
        PostProcessingConfigGroup ppc = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class);
        ppc.setSimulationSampleSize(sampleSize);

        PlansConfigGroup plansConfigGroup = ConfigUtils.addOrGetModule(config, PlansConfigGroup.class);
        plansConfigGroup.setInputFile(plansFile);

        if (args.length > 5) {
            String transit = args[5];
            String zoneFile = args[6];
            if (!transit.equals("-")) {
                config.transit().setTransitScheduleFile(Paths.get(transit, "transitSchedule.xml.gz").toString());
                config.transit().setVehiclesFile(Paths.get(transit, "transitVehicles.xml.gz").toString());
                SBBSupplyConfigGroup supp = ConfigUtils.addOrGetModule(config, SBBSupplyConfigGroup.class);
                supp.setTransitNetworkFile(Paths.get(transit, "transitNetwork.xml.gz").toString());
            }
            if (!zoneFile.equals("-")) {
                ZonesListConfigGroup zonesConfigGroup = ConfigUtils.addOrGetModule(config, ZonesListConfigGroup.class);
                for (ZonesListConfigGroup.ZonesParameterSet group : zonesConfigGroup.getZones()) {
                    group.setFilename(zoneFile);
                }
            }
        }

        new ConfigWriter(config).write(outputFile);
    }

}
