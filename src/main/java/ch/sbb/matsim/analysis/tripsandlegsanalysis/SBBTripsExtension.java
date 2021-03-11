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
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.matsim.analysis.TripsAndLegsCSVWriter.CustomTripsWriterExtension;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.core.router.TripStructureUtils.Trip;

@Singleton
public class SBBTripsExtension implements CustomTripsWriterExtension {

    private final RailTripsAnalyzer railTripsAnalyzer;
    private final Zones zones;
    private final Scenario scenario;

    @Inject
    public SBBTripsExtension(RailTripsAnalyzer railTripsAnalyzer, PostProcessingConfigGroup ppConfig, ZonesCollection zonesCollection, Scenario scenario) {
        this.railTripsAnalyzer = railTripsAnalyzer;
        this.zones = zonesCollection.getZones(ppConfig.getZonesId());
        this.scenario = scenario;
    }

    @Override
    public String[] getAdditionalTripHeader() {
        return new String[]{"from_zone", "to_zone", "first_rail_stop", "last_rail_stop", "rail_pkm"};
    }

    @Override
    public List<String> getAdditionalTripColumns(Trip trip) {
        var fromZone = zones.findZone(getCoordFromActivity(trip.getOriginActivity()));
        var toZone = zones.findZone(getCoordFromActivity(trip.getDestinationActivity()));
        String fromZoneString = fromZone != null ? fromZone.getId().toString() : "";
        String toZoneString = toZone != null ? toZone.getId().toString() : "";
        var railOd = railTripsAnalyzer.getOriginDestination(trip);
        String fromStation = railOd != null ? railOd.getFirst().toString() : "";
        String toStation = railOd != null ? railOd.getSecond().toString() : "";

        String rail_pkm = calcRailPkm(trip);

        return List.of(fromZoneString, toZoneString, fromStation, toStation, rail_pkm);
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
