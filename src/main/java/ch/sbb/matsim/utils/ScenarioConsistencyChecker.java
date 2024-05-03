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

import ch.sbb.matsim.config.SBBReplanningConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet;
import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.StageActivityHandling;
import org.matsim.vehicles.Vehicle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

public class ScenarioConsistencyChecker {

	public static final Logger LOGGER = LogManager.getLogger(ScenarioConsistencyChecker.class);
	private static String logmessage = "";

	public static void checkScenarioConsistency(Scenario scenario) {
		if (!(checkExogeneousShares(scenario) && checkVehicles(scenario) && checkPlans(scenario) && checkIntermodalAttributesAtStops(scenario) && checkIntermodalPopulationExists(scenario) && checkCarAvailableAttributesAreSetProperly(scenario))) {
			throw new RuntimeException(" Error found while checking consistency of plans. Check log!");
		}

	}

	public static void writeLog(String path) {
		try {
			LOGGER.info("Writing scenario log check to " + path);
			FileUtils.writeStringToFile(new File(path), logmessage, Charset.defaultCharset());
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private static boolean checkExogeneousShares(Scenario scenario) {
		boolean checkPassed = true;
		double sum = scenario.getPopulation().getPersons().size();
		Map<String, Integer> subpops = scenario.getPopulation().getPersons().values().stream().map(PopulationUtils::getSubpopulation).filter(Objects::nonNull)
				.collect(Collectors.toMap(s -> s, s -> 1, Integer::sum));
		LOGGER.info("Found the following subpopulations: " + subpops.keySet());

		List<String> invalidSubpopulations = subpops.keySet().stream().filter(k -> !Variables.SUBPOPULATIONS.contains(k)).toList();
		if (invalidSubpopulations.size()>0) {
			LOGGER.error("Invalid subpopulations found: " + String.join(",", invalidSubpopulations));
			checkPassed = false;
		}
		Map<String, Double> shares = Map.of("regular", 0.65, "freight_road", 0.25, "cb_road", 0.08, "cb_rail", 0.0067, "airport_road", 0.0033, "airport_rail", 0.0024);

		final String persons_per_subpopulation = "Persons per Subpopulation";
		LOGGER.info(persons_per_subpopulation);
		logmessage = logmessage + persons_per_subpopulation + "\n";
		LOGGER.info("Subpopulation\tAbsolute\tShare\tShare in MOBi 3.1");
		logmessage = logmessage + "Subpopulation\tAbsolute\tShare\tShare in MOBi 3.1\n";
		for (Entry<String, Integer> e : subpops.entrySet()) {
			Double m31share = shares.get(e.getKey());
			final String message = e.getKey() + "\t" + e.getValue() + "\t" + e.getValue() / sum + "\t" + m31share;
			LOGGER.info(message);
			logmessage = logmessage + message + "\n";
		}
		logmessage = logmessage + "\n";

		return checkPassed;
	}

	private static boolean checkPlans(Scenario scenario) {
		boolean checkPassed = true;
		Set<Person> regularPopulation = scenario.getPopulation().getPersons().values().stream()
				.filter(p -> PopulationUtils.getSubpopulation(p).equals(Variables.REGULAR)).collect(Collectors.toSet());
		if (regularPopulation.isEmpty()) {
			LOGGER.error("No agent in subpopulation" + Variables.REGULAR + " found. ");
			checkPassed = false;

		}
		Set<String> activitytypes = regularPopulation.stream()
				.flatMap(person -> TripStructureUtils.getActivities(person.getSelectedPlan(), StageActivityHandling.ExcludeStageActivities).stream())
				.map(a -> a.getType().split("_")[0])
				.collect(Collectors.toSet());
		Set<String> permissibleActivityTypes = new HashSet<>(SBBActivities.abmActs2matsimActs.values());
		permissibleActivityTypes.add(Variables.OUTSIDE);
		if (!permissibleActivityTypes.containsAll(activitytypes)) {
			LOGGER.error("Detected unknown activity types: \n" + activitytypes + "\n Permissible Types: " + permissibleActivityTypes);
			checkPassed = false;
		}

		Set<String> modes = regularPopulation.stream()
				.flatMap(person -> TripStructureUtils.getLegs(person.getSelectedPlan()).stream())
				.map(Leg::getMode)
				.collect(Collectors.toSet());
		Set<String> permissiblemodes = new HashSet<>(SBBModes.mode2HierarchalNumber.keySet());
		permissiblemodes.add(Variables.OUTSIDE);
		if (!permissiblemodes.containsAll(modes)) {
			LOGGER.error("Detected unknown modes: \n" + modes + "\n Permissible Types: " + permissiblemodes);
			checkPassed = false;
		}
		for (Person  p : regularPopulation){
			var atts = p.getAttributes().getAsMap();
			for (var at : Variables.DEFAULT_PERSON_ATTRIBUTES){
				if (!atts.containsKey(at)) {
					LOGGER.error("Person " + p.getId() + " has no attribute " + at);
					checkPassed = false;
				}
			}
			if (!String.valueOf(p.getAttributes().getAttribute(Variables.CAR_AVAIL)).equals(Variables.CAR_AVAL_TRUE)){
				var usesCar = TripStructureUtils.getLegs(p.getSelectedPlan()).stream().anyMatch(leg -> leg.getMode().equals(SBBModes.CAR));
				if (usesCar) {
					LOGGER.error("Person " + p.getId() + " has no car available, but at least one car trip in initial plan");
					checkPassed = false;
				}
			}


		}
		//all agents (including exogenous demand)
		for (Person p : scenario.getPopulation().getPersons().values()){
			int legs = TripStructureUtils.getLegs(p.getSelectedPlan()).size();
			int acts = TripStructureUtils.getActivities(p.getSelectedPlan(), StageActivityHandling.StagesAsNormalActivities).size();
			if (legs + 1 != acts) {
				LOGGER.error("Person " + p.getId() + " has an inconsistent number of legs and activities in selected plan");
				LOGGER.error(p.getSelectedPlan());
				checkPassed = false;

			}
		}

		return checkPassed;
	}

	public static boolean checkVehicles(Scenario scenario) {
		Set<Id<Vehicle>> allvehicles = new HashSet<>();
		allvehicles.addAll(scenario.getVehicles().getVehicles().keySet());
		allvehicles.addAll(scenario.getTransitVehicles().getVehicles().keySet());
		if (allvehicles.size() != scenario.getVehicles().getVehicles().size() + scenario.getTransitVehicles().getVehicles().size()) {
			LOGGER.error("Some vehicle Ids exist both as transit vehicle Ids and cars.");
			return false;
		}
		return true;
	}

	public static boolean checkCarAvailableAttributesAreSetProperly(Scenario scenario) {
		var carModeAllowedSetting = ConfigUtils.addOrGetModule(scenario.getConfig(), SBBReplanningConfigGroup.class).getCarModeAllowedSetting();
		boolean setProperly = switch (carModeAllowedSetting) {
			case always -> true;
			case carAvailable ->
					scenario.getPopulation().getPersons().values().stream().anyMatch(person -> Variables.CAR_AVAL_TRUE.equals(String.valueOf(person.getAttributes().getAttribute(Variables.CAR_AVAIL))));
			case licenseAvailable ->
					scenario.getPopulation().getPersons().values().stream().anyMatch(person -> Variables.CAR_AVAL_TRUE.equals(String.valueOf(person.getAttributes().getAttribute(Variables.HAS_DRIVING_LICENSE))));


		};
		if (!setProperly) {
			LogManager.getLogger(ScenarioConsistencyChecker.class).error("carModeAllowedSetting in config is set to " + carModeAllowedSetting + " but corresponding attributes are not set for any person in the population.");
		}
		return setProperly;
	}

	public static boolean checkIntermodalAttributesAtStops(Scenario scenario) {
		SwissRailRaptorConfigGroup swissRailRaptorConfigGroup = ConfigUtils.addOrGetModule(scenario.getConfig(), SwissRailRaptorConfigGroup.class);
		boolean checkPassed = true;
		if (swissRailRaptorConfigGroup.isUseIntermodalAccessEgress()) {
			Set<String> intermodalModesAtt = swissRailRaptorConfigGroup.getIntermodalAccessEgressParameterSets().stream().map(IntermodalAccessEgressParameterSet::getStopFilterAttribute)
					.collect(Collectors.toSet());
			intermodalModesAtt.remove(null);
			for (String att : intermodalModesAtt) {
				int count = scenario.getTransitSchedule().getFacilities().values().stream().map(transitStopFacility -> transitStopFacility.getAttributes().getAttribute(att))
						.filter(Objects::nonNull)
						.mapToInt(a -> Integer.valueOf(a.toString()))
						.sum();

				if (count == 0) {
					checkPassed = false;
					LOGGER.error("No stop has a value defined for  " + att);
				} else {
					final String message = "Found " + count + " stops with intermodal access attribute " + att;
					logmessage = logmessage + message + "\n";
					LOGGER.info(message);
				}
			}
		}
		return checkPassed;

	}

	public static boolean checkIntermodalPopulationExists(Scenario scenario) {
		SwissRailRaptorConfigGroup swissRailRaptorConfigGroup = ConfigUtils.addOrGetModule(scenario.getConfig(), SwissRailRaptorConfigGroup.class);
		Set<String> modeAtts = swissRailRaptorConfigGroup.getIntermodalAccessEgressParameterSets().stream().map(IntermodalAccessEgressParameterSet::getPersonFilterAttribute).filter(Objects::nonNull)
				.collect(Collectors.toSet());
		double persons = scenario.getPopulation().getPersons().values().stream().filter(p -> PopulationUtils.getSubpopulation(p).equals(Variables.REGULAR)).count();
		Map<String, Double> mobi32values = Map.of("car2pt", 0.52, "bike2pt", 0.34, "ride2pt", 0.23);
		logmessage = logmessage + "\nMode\tCount\tShare\tShareInMobi3.2\n";
		boolean checkPassed = true;

		for (String att : modeAtts) {
			int count = scenario.getPopulation().getPersons().values().stream().map(p -> p.getAttributes().getAttribute(att)).filter(Objects::nonNull).mapToInt(a -> Integer.parseInt(a.toString()))
					.sum();
			if (count == 0) {
				final String s = "No person has a value defined for  " + att;
				LOGGER.error(s);
				logmessage = logmessage + s + "\n";
				checkPassed = false;
			} else {

				LOGGER.info("Found " + count + " persons with intermodal access attribute " + att);
				double share = count / persons;
				Double share32 = mobi32values.get(att);
				final String message = att + "\t" + count + "\t" + share + "\t" + share32;
				logmessage = logmessage + message + "\n";
			}
		}
		return checkPassed;
	}
}
