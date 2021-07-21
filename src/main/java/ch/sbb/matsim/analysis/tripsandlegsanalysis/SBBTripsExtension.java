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
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.matsim.analysis.TripsAndLegsCSVWriter.CustomTripsWriterExtension;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.StageActivityHandling;
import org.matsim.core.router.TripStructureUtils.Trip;

@Singleton
public class SBBTripsExtension implements CustomTripsWriterExtension {

    private final RailTripsAnalyzer railTripsAnalyzer;
    private final Zones zones;
    private final Scenario scenario;
    private final IdMap<Person, LinkedList<String>> tripIds;

    @Inject
    public SBBTripsExtension(RailTripsAnalyzer railTripsAnalyzer, PostProcessingConfigGroup ppConfig, ZonesCollection zonesCollection, Scenario scenario) {
        this.railTripsAnalyzer = railTripsAnalyzer;
        this.zones = zonesCollection.getZones(ppConfig.getZonesId());
        this.scenario = scenario;
        tripIds = new IdMap<>(Person.class, scenario.getPopulation().getPersons().size());

    }

    @Override
    public String[] getAdditionalTripHeader() {
        //this is always called before a new file is written, so the reset is added here.
        for (var p : scenario.getPopulation().getPersons().values()) {
            LinkedList<String> ids = TripStructureUtils.getActivities(p.getSelectedPlan(), StageActivityHandling.ExcludeStageActivities).stream()
                    .map(activity -> activity.getAttributes().getAttribute(Variables.NEXT_TRIP_ID_ATTRIBUTE)).filter(
                            Objects::nonNull).map(Object::toString).collect(Collectors.toCollection(LinkedList::new));
            tripIds.put(p.getId(), ids);
        }

        return new String[]{"tourId_tripId", "from_zone", "to_zone", "first_rail_stop", "last_rail_stop", "rail_pkm"};
    }

    @Override
    public List<String> getAdditionalTripColumns(Trip trip) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getAdditionalTripColumns(Id<Person> personId, Trip trip) {
        var fromZone = zones.findZone(getCoordFromActivity(trip.getOriginActivity()));
        var toZone = zones.findZone(getCoordFromActivity(trip.getDestinationActivity()));
        String fromZoneString = fromZone != null ? fromZone.getId().toString() : "";
        String toZoneString = toZone != null ? toZone.getId().toString() : "";
        var railOd = railTripsAnalyzer.getOriginDestination(trip);
        String fromStation = railOd != null ? railOd.getFirst().toString() : "";
        String toStation = railOd != null ? railOd.getSecond().toString() : "";
        var visumTripIds = tripIds.get(personId);
        String tourTripId = "";
        if (visumTripIds != null) {
            String id = visumTripIds.poll();
            if (id != null) {
                tourTripId = id;
            }
        }
        String rail_pkm = calcRailPkm(trip);
        final List<String> result = List.of(tourTripId, fromZoneString, toZoneString, fromStation, toStation, rail_pkm);
        return result;
    }

    private String calcRailPkm(Trip trip) {
        var rail_pm = railTripsAnalyzer.calcRailDistance(trip);
        return String.valueOf(rail_pm / 1000);
    }

    private Coord getCoordFromActivity(Activity activity) {
        if (activity.getCoord() != null) {
            return activity.getCoord();
        } else if (activity.getFacilityId() != null && scenario.getActivityFacilities().getFacilities().containsKey(activity.getFacilityId())) {
            Coord coord = scenario.getActivityFacilities().getFacilities().get(activity.getFacilityId()).getCoord();
            return coord != null ? coord : getCoordFromLink(activity.getLinkId());
        } else {
            return getCoordFromLink(activity.getLinkId());
        }

    }

    private Coord getCoordFromLink(Id<Link> linkId) {
        return scenario.getNetwork().getLinks().get(linkId).getToNode().getCoord();
    }

}
