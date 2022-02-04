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

package ch.sbb.matsim.projects.bypassluzern;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

public class IncreaseSubscriptions {

    public static void main(String[] args) throws IOException {
        String inputPopulation = args[0];
        String outputPopulation = args[1];
        double factor = Double.parseDouble(args[2]);
        String zonesWhitelist = args[3];

        var whitelist = Files.lines(Path.of(zonesWhitelist)).collect(Collectors.toSet());
        System.out.println("whitelist " + whitelist);

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(inputPopulation);

        Set<Person> residingAgents = scenario.getPopulation().getPersons().values()
                .stream().filter(p -> whitelist.contains(String.valueOf((Integer) p.getAttributes().getAttribute("residence_zone_id"))))
                .collect(Collectors.toSet());
        System.out.println("residents " + residingAgents.size());

        double subscriptions = residingAgents.stream().filter(p -> "VA".equals(String.valueOf(p.getAttributes().getAttribute("pt_subscr")))).count();
        System.out.println("found " + subscriptions);
        List<Person> agentsWithoutSubscriptions = residingAgents.stream().filter(p -> "none".equals(String.valueOf(p.getAttributes().getAttribute("pt_subscr")))).collect(Collectors.toList());
        int requirecAdditionalSubscriptions = (int) Math.ceil((subscriptions * factor) - subscriptions);
        Collections.shuffle(agentsWithoutSubscriptions);
        for (int i = 0; i < requirecAdditionalSubscriptions; i++) {
            Person p = agentsWithoutSubscriptions.get(i);
            p.getAttributes().putAttribute("pt_subscr", "VA");
        }
        System.out.println("added " + requirecAdditionalSubscriptions);
        new PopulationWriter(scenario.getPopulation()).write(outputPopulation);

    }
}
