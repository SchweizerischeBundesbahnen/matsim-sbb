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

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;

public class PlanAnalyserMobi3 {

	public static void main(String[] args) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Scenario scenario2 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        StreamingPopulationReader streamingPopulationReader = new StreamingPopulationReader(scenario);
        streamingPopulationReader.addAlgorithm(person -> {
            //				String edu = (String) person.getAttributes().getAttribute("current_edu");
            //				if (edu!=null){
            //					if (edu.equalsIgnoreCase(""))
            //				}
            Integer car = (Integer) person.getAttributes().getAttribute(Variables.CAR_AVAIL);
            if (car != null) {
                if (car == 0) {
                    if (TripStructureUtils.getLegs(person.getSelectedPlan()).stream().anyMatch(leg -> leg.getMode().equals(SBBModes.CAR))) {
                        scenario2.getPopulation().addPerson(person);
                    }
                }
            }
        });
        streamingPopulationReader.readFile("\\\\k13536\\mobi\\40_Projekte\\20200330_MOBi_3.0\\sim\\2.9.x\\2.9.3\\qsim\\prepared\\populationMerged\\plans.xml.gz");
		new PopulationWriter(scenario2.getPopulation()).write("faultycarplans.xml.gz");
	}
}
