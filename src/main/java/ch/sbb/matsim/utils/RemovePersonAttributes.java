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

package ch.sbb.matsim.utils;

import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.algorithms.TripsToLegsAlgorithm;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.StageActivityHandling;
import org.matsim.core.scenario.ScenarioUtils;

public class RemovePersonAttributes {

    public static void main(String[] args) {
        StreamingPopulationWriter spw = new StreamingPopulationWriter();
        TripsToLegsAlgorithm tripsToLegsAlgorithm = new TripsToLegsAlgorithm(new SBBIntermodalAwareRouterModeIdentifier(ConfigUtils.createConfig()));
        spw.startStreaming("C:\\devsbb\\zugplans_simplified.xml.gz");
        StreamingPopulationReader spr = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
        spr.addAlgorithm(person -> {

            PersonUtils.removeUnselectedPlans(person);
            TripStructureUtils.getActivities(person.getSelectedPlan(), StageActivityHandling.ExcludeStageActivities).forEach(activity -> {
                var type = activity.getType().split("_")[0];
                activity.setType(type);
            });
            tripsToLegsAlgorithm.run(person.getSelectedPlan());
            spw.run(person);

        });
        spr.readFile("C:\\devsbb\\zugplans.xml.gz");
        spw.closeStreaming();
    }

}
