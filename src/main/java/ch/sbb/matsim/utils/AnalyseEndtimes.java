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

import ch.sbb.matsim.config.variables.Variables;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.algorithms.PersonAlgorithm;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.StageActivityHandling;
import org.matsim.core.scenario.ScenarioUtils;

public class AnalyseEndtimes {

	public static void main(String[] args) {
		int[] initialEndtimes = new int[32];
		int[] undefined = new int[1];
		StreamingPopulationReader streamingPopulationReader = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
		streamingPopulationReader.addAlgorithm(new PersonAlgorithm() {
			@Override
			public void run(Person person) {
				if (PopulationUtils.getSubpopulation(person).equals(Variables.REGULAR)) {
					TripStructureUtils.getActivities(person.getSelectedPlan(), StageActivityHandling.ExcludeStageActivities).forEach(a -> {
						if (a.getEndTime().isDefined()) {
							int hour = (int) (a.getEndTime().seconds() / 3600.);
							initialEndtimes[hour]++;
						} else {
							undefined[0]++;
						}
					});
				}
			}
		});
		streamingPopulationReader.readFile("\\\\k13536\\mobi\\50_Ergebnisse\\MOBi_2.2\\sim\\2.2.3_100pct\\prepared\\populationMerged\\sliced_10\\plans_0.xml.gz");

		for (int i = 0; i < initialEndtimes.length; i++) {
			System.out.println(i + " \t" + initialEndtimes[i]);
		}
		System.out.println(" no time defined " + undefined[0]);
	}

}
