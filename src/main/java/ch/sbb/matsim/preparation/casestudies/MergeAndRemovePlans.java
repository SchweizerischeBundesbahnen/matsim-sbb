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

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.FacilitiesWriter;
import org.matsim.facilities.MatsimFacilitiesReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MergeAndRemovePlans {

    final String inputPlans;
    final String blackListCsv;
    final String additionalPlans;
    final String outputPlans;
    final String inputFacilities;
    final String additionalFacilities;
    final String outputFacilities;
    Set<Id<Person>> blacklist;

    /**
     * @param args inputplans = original plans file, blackListCSV = List of plans that needs to be deleted, additionalPlans = newly generated plans
     */
    public MergeAndRemovePlans(String[] args) {
        inputPlans = args[0];
        blackListCsv = args[1];
        additionalPlans = args[2];
        outputPlans = args[3];
        inputFacilities = args[4];
        additionalFacilities = args[5];
        outputFacilities = args[6];
    }

    public static void main(String[] args) throws IOException {
        new MergeAndRemovePlans(args).run();
    }

    private void run() throws IOException {
        try(Stream<String> lines = Files.lines(Path.of(blackListCsv))) {
            blacklist = lines.map(Id::createPersonId).collect(Collectors.toSet());
        }
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(additionalPlans);
        var spw = new StreamingPopulationWriter();
        spw.startStreaming(outputPlans);
        for (Person p : scenario.getPopulation().getPersons().values()) {
            spw.run(p);
        }
        var spr = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
        spr.addAlgorithm(person -> {
            if (!(this.blacklist.contains(person.getId()))) {
                if (!(scenario.getPopulation().getPersons().containsKey(person.getId()))) {
                    spw.run(person);
                }
            }
        });
        spr.readFile(inputPlans);
        spw.closeStreaming();
        new MatsimFacilitiesReader(scenario).readFile(inputFacilities);
        Scenario scenario2 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimFacilitiesReader(scenario2).readFile(additionalFacilities);
        for (ActivityFacility af : scenario2.getActivityFacilities().getFacilities().values()) {
            if (!scenario.getActivityFacilities().getFacilities().containsKey(af.getId())) {
                scenario.getActivityFacilities().addActivityFacility(af);
            }
        }
        new FacilitiesWriter(scenario.getActivityFacilities()).write(outputFacilities);
    }
}
