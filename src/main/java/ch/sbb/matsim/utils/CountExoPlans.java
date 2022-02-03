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

import ch.sbb.matsim.csv.CSVWriter;
import java.io.IOException;
import java.util.Set;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

public class CountExoPlans {

    public static void main(String[] args) {
        String basepath = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220114_MOBi_3.3\\";
        Set<String> years = Set.of("2017", "2030", "2040", "2050");
        Set<String> segments = Set.of("airport_rail", "airport_road", "cb_rail", "cb_road", "tourism_rail", "freight_road");
        try (CSVWriter writer = new CSVWriter(null, new String[]{"year", "segment", "agents"}, basepath + "\\plans_exogeneous\\stats.csv")) {
            for (var s : segments) {
                for (var y : years) {
                    String path = basepath + y + "\\plans_exogeneous\\" + s + "\\100pct\\plans.xml.gz";
                    Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
                    new PopulationReader(scenario).readFile(path);
                    writer.set("year", y);
                    writer.set("segment", s);
                    writer.set("agents", Integer.toString(scenario.getPopulation().getPersons().size()));
                    writer.writeRow();
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
