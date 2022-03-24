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

package ch.sbb.matsim.projects.roche;

import ch.sbb.matsim.config.variables.Variables;
import org.apache.commons.lang3.mutable.MutableInt;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.StageActivityHandling;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RemoveCurrentAndMergeNewRocheEmployees {

    public static void main(String[] args) throws IOException {
        String inputPlans = args[0];
        String outputPlans = args[1];
        String facilitiesblacklist = args[2];
        String rocheplans = args[3];
        Set<String> blacklistFacilities;
        try(Stream<String> lines = Files.lines(Path.of(facilitiesblacklist))) {
            blacklistFacilities = lines.collect(Collectors.toSet());
        }
        MutableInt i = new MutableInt();
        StreamingPopulationWriter streamingPopulationWriter = new StreamingPopulationWriter();
        streamingPopulationWriter.startStreaming(outputPlans);
        StreamingPopulationReader streamingPopulationReader = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
        streamingPopulationReader.addAlgorithm(person -> {
            boolean filterout = TripStructureUtils.getActivities(person.getSelectedPlan(), StageActivityHandling.ExcludeStageActivities).stream().map(Activity::getFacilityId
            ).filter(Objects::nonNull).anyMatch(f -> blacklistFacilities.contains(f.toString()));
            if (!filterout) {
                PopulationUtils.putSubpopulation(person, Variables.NO_REPLANNING);
                streamingPopulationWriter.run(person);
            } else {
                i.increment();
            }
        });
        streamingPopulationReader.readFile(inputPlans);
        StreamingPopulationReader rochpop = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
        rochpop.addAlgorithm(streamingPopulationWriter);
        rochpop.readFile(rocheplans);

        streamingPopulationWriter.closeStreaming();
        System.out.println("Filtered out " + i);
    }

}
