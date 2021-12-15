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
import java.util.Random;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
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
        final Population population = scenario.getPopulation();
        final Population population1 = scenario2.getPopulation();

        scalePopulation(desiredPlans, population, population1);
        new PopulationWriter(population1).write(outputPlans);

    }

    public static void scalePopulation(int desiredPlans, Population basePopulation, Population scaledPopulation) {
        List<Id<Person>> pickedPersons = new ArrayList<>(basePopulation.getPersons().keySet());
        final Random random = MatsimRandom.getRandom();
        Collections.shuffle(pickedPersons, random);

        if (desiredPlans <= pickedPersons.size()) {
            for (int i = 0; i < desiredPlans; i++) {
                Id<Person> personId = pickedPersons.get(i);
                scaledPopulation.addPerson(basePopulation.getPersons().get(personId));
            }
        } else {
            basePopulation.getPersons().values().forEach(p -> scaledPopulation.addPerson(p));
            for (int i = 0; i < desiredPlans - pickedPersons.size(); i++) {
                Id<Person> personId = pickedPersons.get(random.nextInt(pickedPersons.size()));
                Person p = basePopulation.getPersons().get(personId);
                Person clone = scaledPopulation.getFactory().createPerson(Id.createPersonId(p.getId().toString() + "_clone_" + i));
                clone.addPlan(p.getSelectedPlan());
                p.getAttributes().getAsMap().forEach((k, v) -> clone.getAttributes().putAttribute(k, v));
                scaledPopulation.addPerson(clone);
            }
        }
    }
}
