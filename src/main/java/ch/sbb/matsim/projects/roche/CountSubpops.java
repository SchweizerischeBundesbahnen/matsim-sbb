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

import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.stream.Collectors;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

public class CountSubpops {

    public static void main(String[] args) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile("\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_3.1\\sim\\3.1.3_25pct\\prepared\\plans_10\\population_0.xml.gz");
        double sum = scenario.getPopulation().getPersons().size();

        Map<String, Integer> subpops = scenario.getPopulation().getPersons().values().stream().map(PopulationUtils::getSubpopulation).filter(Objects::nonNull).collect(
                Collectors.toMap(s -> s, s -> 1, Integer::sum));
        System.out.println("Found the following subpopulations: " + subpops.keySet());

        System.out.println("Persons per Subpopulation");
        System.out.println("Subpopulation\tAbsolute\tShare");
        for (Entry<String, Integer> e : subpops.entrySet()) {
            System.out.println(e.getKey() + "\t" + e.getValue() + "\t" + e.getValue() / sum);
        }
    }

}
