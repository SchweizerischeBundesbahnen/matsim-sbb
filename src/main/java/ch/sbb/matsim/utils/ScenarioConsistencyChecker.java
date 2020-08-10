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

import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.StageActivityHandling;
import org.matsim.vehicles.Vehicle;

public class ScenarioConsistencyChecker {

	public static final Logger LOGGER = Logger.getLogger(ScenarioConsistencyChecker.class);

	public static void checkScenarioConsistency(Scenario scenario) {
		if(!(checkVehicles(scenario) && checkPlans(scenario))){
			throw new RuntimeException(" Error found while checking consistency of plans. Check log!");
		};
	}

	private static boolean checkPlans(Scenario scenario) {
		boolean result = true;
		Set<Person> regularPopulation = scenario.getPopulation().getPersons().values().stream()
				.filter(p-> PopulationUtils.getSubpopulation(p).equals(Variables.REGULAR)).collect(Collectors.toSet());
		Set<String> activitytypes = regularPopulation.stream()
				.flatMap(person -> TripStructureUtils.getActivities(person.getSelectedPlan(),StageActivityHandling.ExcludeStageActivities).stream())
				.map(a->a.getType().split("_")[0])
				.collect(Collectors.toSet());
		Set<String> permissibleActivityTypes = new HashSet<>(SBBActivities.abmActs2matsimActs.values());
		permissibleActivityTypes.add("outside");
		if (!permissibleActivityTypes.containsAll(activitytypes)){
			LOGGER.error("Detected unknown activity types: \n"+activitytypes+"\n Permissible Types: "+ permissibleActivityTypes);
			result= false;
		}

		Set<String> modes = regularPopulation.stream()
				.flatMap(person -> TripStructureUtils.getLegs(person.getSelectedPlan()).stream())
				.map(a->a.getMode())
				.collect(Collectors.toSet());
		if (!SBBModes.mode2HierarchalNumber.keySet().containsAll(modes)){
			LOGGER.error("Detected unknown modes: \n"+modes+"\n Permissible Types: "+ SBBModes.mode2HierarchalNumber.keySet());
			result= false;
		}

		for (Person  p : regularPopulation){
			var atts = p.getAttributes().getAsMap();
			for (var at : Variables.DEFAULT_PERSON_ATTRIBUTES){
				if (!atts.containsKey(at)){
					LOGGER.error("Person "+p.getId() + " has no attribute "+at);
					result = false;
				}
			}
		if (!String.valueOf(p.getAttributes().getAttribute(Variables.CAR_AVAIL)).equals(Variables.CAR_AVAL_TRUE)){
			var usesCar = TripStructureUtils.getLegs(p.getSelectedPlan()).stream().anyMatch(leg -> leg.getMode().equals(SBBModes.CAR));
			if (usesCar){
				LOGGER.error("Person "+p.getId() + " has no car available, but at least one car trip in initial plan");
				result = false;
			}
		}


		}
		//all agents (including exogenous demand)
		for (Person p : scenario.getPopulation().getPersons().values()){
			int legs = TripStructureUtils.getLegs(p.getSelectedPlan()).size();
			int acts = TripStructureUtils.getActivities(p.getSelectedPlan(), StageActivityHandling.StagesAsNormalActivities).size();
			if (legs+1 != acts){
				LOGGER.error("Person "+p.getId()+" has an inconsistent number of legs and activities in selected plan");
				LOGGER.error(p.getSelectedPlan());
				result = false;

			}
		}

		return result;
	}

	public static boolean checkVehicles(Scenario scenario) {
		Set<Id<Vehicle>> allvehicles = new HashSet<>();
		allvehicles.addAll(scenario.getVehicles().getVehicles().keySet());
		allvehicles.addAll(scenario.getTransitVehicles().getVehicles().keySet());
		if (allvehicles.size() != scenario.getVehicles().getVehicles().size()+scenario.getTransitVehicles().getVehicles().size()){
			LOGGER.error("Some vehicle Ids exist both as transit vehicle Ids and cars.");
			return false;
		}
		return true;
	}
}
