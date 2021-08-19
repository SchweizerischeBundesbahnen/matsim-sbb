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

import ch.sbb.matsim.csv.CSVWriter;
import java.io.IOException;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

public class Modesanalyser {

    public static void main(String[] args) {
        String outputplans = args[0];
        String csvout = args[1];
        try (CSVWriter writer = new CSVWriter(null, new String[]{"personId", "desiredMode", "actualMode"}, csvout)) {
            StreamingPopulationReader spr = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
            spr.addAlgorithm(person -> {
                if (person.getId().toString().startsWith("r_")) {
                    writer.set("personId", person.getId().toString());
                    String routingMode = (String) person.getSelectedPlan().getPlanElements().get(1).getAttributes().getAttribute("routingMode");
                    String desiredMode = (String) person.getAttributes().getAttribute("tuesday_mode");
                    writer.set("desiredMode", String.valueOf(desiredMode));
                    writer.set("actualMode", String.valueOf(routingMode));
                    writer.writeRow();
                }
            });
            spr.readFile(outputplans);

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

}
