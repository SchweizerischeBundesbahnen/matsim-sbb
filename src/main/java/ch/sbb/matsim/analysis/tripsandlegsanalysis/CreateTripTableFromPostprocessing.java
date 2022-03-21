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

package ch.sbb.matsim.analysis.tripsandlegsanalysis;

import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.preparation.cutter.BetterPopulationReader;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesLoader;
import java.io.File;
import org.matsim.analysis.TripsAndLegsCSVWriter;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.MatsimFacilitiesReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

public class CreateTripTableFromPostprocessing {

    public static void main(String[] args) {

        String networkFile = args[0];
        String facilitiesFile = args[1];
        String transitScheduleFile = args[2];
        String populationFile = args[3];
        String experiencedPlansFile = args[4];
        String zonesFile = args[5];
        String outputTripsFile = args[6];
        String outputLegsFile = args[7];

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Scenario scenario2 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        PostProcessingConfigGroup postProcessingConfigGroup = new PostProcessingConfigGroup();
        postProcessingConfigGroup.setZonesId("zones");

        ZonesCollection zonesCollection = new ZonesCollection();
        zonesCollection.addZones(ZonesLoader.loadZones("zones", zonesFile, "zone_id"));
        new TransitScheduleReader(scenario).readFile(transitScheduleFile);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);
        new MatsimFacilitiesReader(scenario).readFile(facilitiesFile);
        new PopulationReader(scenario2).readFile(experiencedPlansFile);
        BetterPopulationReader.readSelectedPlansOnly(scenario, new File(populationFile));
        RailTripsAnalyzer railTripsAnalyzer = new RailTripsAnalyzer(scenario.getTransitSchedule(), scenario.getNetwork());
        SBBTripsExtension sbbTripsExtension = new SBBTripsExtension(railTripsAnalyzer, postProcessingConfigGroup, zonesCollection, scenario);
        SBBLegsExtension sbbLegsExtension = new SBBLegsExtension(railTripsAnalyzer);
        TripsAndLegsCSVWriter tripsAndLegsCSVWriter = new TripsAndLegsCSVWriter(scenario, sbbTripsExtension, sbbLegsExtension, null, v -> Long.toString((long) v));
        IdMap<Person, Plan> plans = new IdMap<>(Person.class);
        for (Person p : scenario2.getPopulation().getPersons().values()) {
            plans.put(p.getId(), p.getSelectedPlan());
        }
        tripsAndLegsCSVWriter.write(plans, outputTripsFile, outputLegsFile);
    }

}
