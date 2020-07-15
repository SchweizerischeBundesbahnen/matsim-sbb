/* *********************************************************************** *
 * project: org.matsim.*												   *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
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

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.vehicles.MatsimVehicleReader;

/**
 * @author jlie
 * 		<p>
 * 		Based on package org.matsim.contrib.travelsummary.events2traveldiaries.RunEventsToTravelDiearies.java Not properly cleaned!
 * 		</p>
 */
public class RunEventsToEventsPerPersonTable {

	private static final Logger log = Logger.getLogger(RunEventsToEventsPerPersonTable.class);

	public static void main(String[] args) {

		String eventsFileName = null;
		Config config = null;
		String outputDirectory = null;
		String personIdString = null;
		printHelp();
		try {
			config = ConfigUtils.loadConfig(args[0]);
			outputDirectory = config.controler().getOutputDirectory();
			eventsFileName = args[1];
			if (args.length == 3) {
				personIdString = args[2];
			}
		} catch (ArrayIndexOutOfBoundsException e) {
			System.exit(1);
		}
		Scenario scenario = ScenarioUtils.createScenario(config);

		new MatsimNetworkReader(scenario.getNetwork()).readFile(config.network().getInputFile());

		if (config.transit().isUseTransit()) {
			new TransitScheduleReader(scenario)
					.readFile(config.transit().getTransitScheduleFile());
			new MatsimVehicleReader(scenario.getTransitVehicles())
					.readFile(config.transit().getVehiclesFile());
		}
		EventsToEventsPerPersonTable handler;
		if (personIdString == null) {
			handler = new EventsToEventsPerPersonTable(scenario, outputDirectory);
		} else {
			handler = new EventsToEventsPerPersonTable(scenario, outputDirectory);
			handler.setPersonIdString(personIdString);
		}

		EventsManager events = new EventsManagerImpl();

		events.addHandler(handler);

		new MatsimEventsReader(events).readFile(eventsFileName);

		handler.writeResults(true);

		log.info("Number of stuck vehicles/passengers: " + handler.getStuck());
	}

	private static void printHelp() {
		log.info("Just for internal use at SBB\n");
	}
}

