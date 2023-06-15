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

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVReader;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AddIntermodalAttributes {

    public static void main(String[] args) throws MalformedURLException {
        String inputPlansFile = args[0];
        String inputCSVFile = args[1];
        String outputPlans = args[2];
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(inputPlansFile);
        preparePopulation(scenario.getPopulation(), new File(inputCSVFile).toURI().toURL());
        new PopulationWriter(scenario.getPopulation()).write(outputPlans);

    }

    public static void preparePopulation(Population population, URL csvPath) {
        try (CSVReader reader = new CSVReader(csvPath, ";")) {
            Set<String> attributes = new HashSet<>(Arrays.asList(reader.getColumns()));
            if (!attributes.contains(Variables.PERSONID)) {
                throw new RuntimeException("CSV file does not contain a " + Variables.PERSONID + " field in header.");
            }
            Map<String, String> map;
            while ((map = reader.readLine()) != null) {
                final String personIdString = map.get(Variables.PERSONID);
                Id<Person> personId = Id.createPersonId(personIdString);
                Person person = population.getPersons().get(personId);
                if (person != null) {
                    for (String attribute : attributes) {
                        if (person.getAttributes().getAsMap().containsKey(attribute)) {
                            throw new RuntimeException("Attribute " + attribute + " already exists. Overwriting by CSV should not be intended.");
                        }
                        person.getAttributes().putAttribute(attribute, map.get(attribute));
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }
}
