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

package ch.sbb.matsim.preparation.casestudies;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.utils.SBBTripsToLegsAlgorithm;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.router.RoutingModeMainModeIdentifier;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.StageActivityHandling;
import org.matsim.core.scenario.ScenarioUtils;

public class PreparePlansBypassLuzern {

    Set<Id<Link>> relevantLinkIds;

    public static void main(String[] args) throws IOException {
        String networkFile = args[0];
        String zonesFile = args[1];
        String relevantZones = args[2];
        String outputfile = args[3];
        String inputPopulation1 = args[4];
        String inputPopulation2 = args.length > 5 ? args[5] : null;

        new PreparePlansBypassLuzern().run(networkFile, zonesFile, relevantZones, outputfile, inputPopulation1, inputPopulation2);
    }

    private void run(String networkFile, String zonesFile, String relevantZonesFile, String outputfile, String inputPopulation1, String inputPopulation2) throws IOException {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);
        Zones zones = ZonesLoader.loadZones("zones", zonesFile, "zone_id");
        Set<Id<Zone>> relevantZones = Files.lines(Path.of(relevantZonesFile)).map(t -> Id.create(t, Zone.class)).collect(Collectors.toSet());
        fillRelevantLinkIds(scenario.getNetwork(), zones, relevantZones);

        StreamingPopulationWriter streamingPopulationWriter = new StreamingPopulationWriter();
        streamingPopulationWriter.startStreaming(outputfile);
        runPopulation(inputPopulation1, streamingPopulationWriter, relevantZones, zones);
        runPopulation(inputPopulation2, streamingPopulationWriter, relevantZones, zones);
        streamingPopulationWriter.closeStreaming();
    }

    private void runPopulation(String inputPopulationFile, StreamingPopulationWriter streamingPopulationWriter, Set<Id<Zone>> relevantZones, Zones zones) {
        RoutingModeMainModeIdentifier m = new RoutingModeMainModeIdentifier();
        SBBTripsToLegsAlgorithm algorithm = new SBBTripsToLegsAlgorithm(m, Set.of(SBBModes.CAR, SBBModes.RIDE, SBBModes.AVTAXI));
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        StreamingPopulationReader spr = new StreamingPopulationReader(scenario);
        spr.addAlgorithm(person -> {
            boolean copy = false;
            Plan plan = person.getSelectedPlan();
            copy = TripStructureUtils.getTrips(plan)
                    .stream()
                    .flatMap(trip -> trip.getLegsOnly().stream())
                    .filter(leg -> leg.getMode().equals(SBBModes.CAR))
                    .map(leg -> (NetworkRoute) leg.getRoute())
                    .anyMatch(networkRoute -> networkRoute.getLinkIds().stream().anyMatch(l -> relevantLinkIds.contains(l)));
            if (!copy) {
                copy = TripStructureUtils.getActivities(plan, StageActivityHandling.StagesAsNormalActivities).stream().anyMatch(activity -> {
                    Zone zone = zones.findZone(activity.getCoord());
                    if (zone != null) {
                        return relevantZones.contains(zone.getId());
                    }
                    return false;
                });
            }
            if (copy) {
                PersonUtils.removeUnselectedPlans(person);
                algorithm.run(person.getSelectedPlan());
                streamingPopulationWriter.run(person);
            }
        });

        spr.readFile(inputPopulationFile);

    }

    private void fillRelevantLinkIds(Network network, Zones zones, Set<Id<Zone>> relevantZones) {
        this.relevantLinkIds = network.getLinks().values()
                .parallelStream()
                .filter(l -> {
                    var zone = zones.findZone(l.getCoord());
                    if (zone != null) {
                        return relevantZones.contains(zone.getId());
                    }
                    return false;
                })
                .map(Identifiable::getId)
                .collect(Collectors.toSet());
    }

}
