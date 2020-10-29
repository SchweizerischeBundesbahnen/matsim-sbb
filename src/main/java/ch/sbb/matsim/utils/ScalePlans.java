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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

public class ScalePlans {

    public static void main(String[] args) {
        String inputPlans = args[0];
        String outputPlans = args[1];
        int desiredPlans = Integer.parseInt(args[2]);

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Scenario scenario2 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(inputPlans);
        List<Id<Person>> pickedPersons = new ArrayList<>(scenario.getPopulation().getPersons().keySet());
        Collections.shuffle(pickedPersons, MatsimRandom.getRandom());
        for (int i = 0; i < desiredPlans; i++) {
            Id<Person> personId = pickedPersons.get(i);
            scenario2.getPopulation().addPerson(scenario.getPopulation().getPersons().get(personId));
        }
        new PopulationWriter(scenario2.getPopulation()).write(outputPlans);

    }
}
