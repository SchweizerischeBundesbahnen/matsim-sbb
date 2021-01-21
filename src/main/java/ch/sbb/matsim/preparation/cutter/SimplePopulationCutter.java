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

package ch.sbb.matsim.preparation.cutter;

import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.StageActivityHandling;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.FacilitiesWriter;
import org.matsim.facilities.MatsimFacilitiesReader;

/**
 * Cuts all agent with at least one activity in the extent.
 */
public class SimplePopulationCutter {

    public static void main(String[] args) {
        String inputPopulation = args[0];
        String inputFacilities = args[1];
        String inputShape = args[2];
        String outputPopulation = args[3];
        String outputFacilities = args[4];
        boolean deleteAttributes = false;
        boolean onlyInsideAgents = true;
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Zones z = ZonesLoader.loadZones("zones", inputShape, "zone_id");
        new MatsimFacilitiesReader(scenario).readFile(inputFacilities);
        Set<Id<ActivityFacility>> facilitiesToKeep = new HashSet<>();
        StreamingPopulationWriter streamingPopulationWriter = new StreamingPopulationWriter();
        streamingPopulationWriter.startStreaming(outputPopulation);
        StreamingPopulationReader spr = new StreamingPopulationReader(scenario);
        spr.addAlgorithm(person -> {
            Plan plan = person.getSelectedPlan();
            boolean keep = onlyInsideAgents ?
                    TripStructureUtils.getActivities(plan, StageActivityHandling.StagesAsNormalActivities).stream().allMatch(a -> z.findZone(a.getCoord()) != null) :
                    TripStructureUtils.getActivities(plan, StageActivityHandling.StagesAsNormalActivities).stream().anyMatch(a -> z.findZone(a.getCoord()) != null);

            if (keep) {
                facilitiesToKeep.addAll(TripStructureUtils.getActivities(plan, StageActivityHandling.ExcludeStageActivities).stream().map(activity -> activity.getFacilityId()).filter(Objects::nonNull)
                        .collect(Collectors.toSet()));
                if (deleteAttributes) {
                    person.getAttributes().getAsMap().keySet().forEach(p -> person.getAttributes().removeAttribute(p));
                }
                streamingPopulationWriter.run(person);
            }
        });
        spr.readFile(inputPopulation);
        streamingPopulationWriter.closeStreaming();
        Scenario scenario1 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        for (Id<ActivityFacility> f : facilitiesToKeep) {
            scenario1.getActivityFacilities().addActivityFacility(scenario.getActivityFacilities().getFacilities().get(f));
        }
        new FacilitiesWriter(scenario1.getActivityFacilities()).write(outputFacilities);
    }
}
