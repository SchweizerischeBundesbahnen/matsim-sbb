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
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesLoader;
import org.matsim.analysis.TripsAndLegsCSVWriter;
import org.matsim.analysis.TripsAndLegsCSVWriter.CustomLegsWriterExtension;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.MatsimFacilitiesReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

import java.util.Collections;
import java.util.List;

public class CreateTripTableFromPostprocessing {

    public static void main(String[] args) {

        String networkFile = args[0];
        String facilitiesFile = args[1];
        String transitScheduleFile = args[2];
        String experiencedPlansFile = args[3];
        String zonesFile = args[4];
        String outputTripsFile = args[5];
        String outputLegsFile = args[6];

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        PostProcessingConfigGroup postProcessingConfigGroup = new PostProcessingConfigGroup();
        postProcessingConfigGroup.setZonesId("zones");

        ZonesCollection zonesCollection = new ZonesCollection();
        zonesCollection.addZones(ZonesLoader.loadZones("zones", zonesFile, "zone_id"));
        new TransitScheduleReader(scenario).readFile(transitScheduleFile);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);
        new MatsimFacilitiesReader(scenario).readFile(facilitiesFile);
        new PopulationReader(scenario).readFile(experiencedPlansFile);

        RailTripsAnalyzer railTripsAnalyzer = new RailTripsAnalyzer(scenario.getTransitSchedule(), scenario.getNetwork());
        SBBTripsExtension sbbTripsExtension = new SBBTripsExtension(railTripsAnalyzer, postProcessingConfigGroup, zonesCollection, scenario);
        SBBLegsExtension sbbLegsExtension = new SBBLegsExtension(railTripsAnalyzer);
        TripsAndLegsCSVWriter tripsAndLegsCSVWriter = new TripsAndLegsCSVWriter(scenario, sbbTripsExtension, sbbLegsExtension, null, v -> Long.toString((long) v));
        IdMap<Person, Plan> plans = new IdMap<>(Person.class);
        for (Person p : scenario.getPopulation().getPersons().values()) {
            plans.put(p.getId(), p.getSelectedPlan());
        }
        tripsAndLegsCSVWriter.write(plans, outputTripsFile, outputLegsFile);
    }

    static class NoLegsWriterExtension implements CustomLegsWriterExtension {

        @Override
        public String[] getAdditionalLegHeader() {
            return new String[0];
        }

        @Override
        public List<String> getAdditionalLegColumns(TripStructureUtils.Trip experiencedTrip, Leg experiencedLeg) {
            return Collections.emptyList();
        }
    }
}
