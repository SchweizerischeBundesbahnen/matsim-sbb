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
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import org.matsim.analysis.TripsAndLegsCSVWriter.CustomTripsWriterExtension;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.*;

@Singleton
public class SBBTripsExtension implements CustomTripsWriterExtension {

    private final RailTripsAnalyzer railTripsAnalyzer;
    private final Zones zones;
    private final Scenario scenario;
    private IdMap<Person, LinkedList<Variables.MOBiTripAttributes>> tripAttributes;

    @Inject
    public SBBTripsExtension(RailTripsAnalyzer railTripsAnalyzer, PostProcessingConfigGroup ppConfig, ZonesCollection zonesCollection, Scenario scenario) {
        this.railTripsAnalyzer = railTripsAnalyzer;
        this.zones = zonesCollection.getZones(ppConfig.getZonesId());
        this.scenario = scenario;


    }

    @Override
    public String[] getAdditionalTripHeader() {
        //this is always called before a new file is written, so the reset is added here.
        tripAttributes = Variables.MOBiTripAttributes.extractTripAttributes(scenario.getPopulation());
        return new String[]{"from_zone", "to_zone", "first_rail_stop", "last_rail_stop", "rail_pkm", "fq_rail_pkm", "rail_legs", "rail_access_modes", "rail_access_distance", "rail_egress_modes",
                "rail_egress_distance", Variables.MOBiTripAttributes.TOUR_ID, Variables.MOBiTripAttributes.TRIP_ID, Variables.MOBiTripAttributes.PURPOSE, Variables.MOBiTripAttributes.DIRECTION};
    }

    @Override
    public List<String> getAdditionalTripColumns(Trip trip) {
        return Collections.emptyList();
    }

    public static Coord getCoordFromActivity(Activity activity, Scenario scenario) {
        if (activity.getCoord() != null) {
            return activity.getCoord();
        } else if (activity.getFacilityId() != null && scenario.getActivityFacilities().getFacilities().containsKey(activity.getFacilityId())) {
            Coord coord = scenario.getActivityFacilities().getFacilities().get(activity.getFacilityId()).getCoord();
            return coord != null ? coord : getCoordFromLink(activity.getLinkId(), scenario.getNetwork());
        } else {
            return getCoordFromLink(activity.getLinkId(), scenario.getNetwork());
        }

    }

    private Tuple<String, Integer> findAccessMode(Trip trip, Id<TransitStopFacility> accessStop) {
        Set<String> accessModes = new HashSet<>();
        double accesDistance = 0.;

        for (Leg leg : trip.getLegsOnly()) {
            if (leg.getRoute() instanceof TransitPassengerRoute) {
                TransitPassengerRoute route = (TransitPassengerRoute) leg.getRoute();
                if (accessStop.equals(route.getAccessStopId())) {
                    break;
                }
            }
            accessModes.add(leg.getMode());
            accesDistance += leg.getRoute().getDistance();
        }
        if (accessModes.size() > 1) {
            accessModes.remove(SBBModes.ACCESS_EGRESS_WALK);
        }

        return new Tuple<>(CollectionUtils.setToString(accessModes), (int) Math.round(accesDistance));
    }

    private Tuple<String, Integer> findEgressMode(Trip trip, Id<TransitStopFacility> egressStop) {
        Set<String> egressModes = new HashSet<>();
        double egressDistance = 0.;
        boolean startCount = false;
        for (Leg leg : trip.getLegsOnly()) {
            if (startCount) {
                egressModes.add(leg.getMode());
                egressDistance += leg.getRoute().getDistance();
            }
            if (leg.getRoute() instanceof TransitPassengerRoute) {
                TransitPassengerRoute route = (TransitPassengerRoute) leg.getRoute();
                if (egressStop.equals(route.getEgressStopId())) {
                    startCount = true;
                }
            }
        }

        if (egressModes.size() > 1) {
            egressModes.remove(SBBModes.ACCESS_EGRESS_WALK);
        }
        return new Tuple<>(CollectionUtils.setToString(egressModes), (int) Math.round(egressDistance));
    }

    private String calcRailPkm(Trip trip) {
        var rail_pm = railTripsAnalyzer.calcRailDistance(trip);
        return String.valueOf(rail_pm / 1000);
    }

    private String calcfqRailPkm(Trip trip) {
        var rail_pm = railTripsAnalyzer.getFQDistance(trip, true);
        return String.valueOf(rail_pm / 1000);
    }

    private static Coord getCoordFromLink(Id<Link> linkId, Network network) {
        return network.getLinks().get(linkId).getToNode().getCoord();
    }

    @Override
    public List<String> getAdditionalTripColumns(Id<Person> personId, Trip trip) {
        var fromZone = zones.findZone(getCoordFromActivity(trip.getOriginActivity(), scenario));
        var toZone = zones.findZone(getCoordFromActivity(trip.getDestinationActivity(), scenario));
        String fromZoneString = fromZone != null ? fromZone.getId().toString() : "";
        String toZoneString = toZone != null ? toZone.getId().toString() : "";
        var railOd = railTripsAnalyzer.getOriginDestination(trip);
        String fromStation = railOd != null ? railOd.getFirst().toString() : "";
        String toStation = railOd != null ? railOd.getSecond().toString() : "";
        var visumTripIds = tripAttributes.get(personId);
        String tourId = "";
        String tripId = "";
        String direction = "";
        String purpose = "";
        if (visumTripIds != null) {
            Variables.MOBiTripAttributes tripAttributes = visumTripIds.poll();
            if (tripAttributes != null) {
                tourId = tripAttributes.getTourId();
                tripId = tripAttributes.getTripId();
                direction = tripAttributes.getTripDirection();
                purpose = tripAttributes.getTripPurpose();
            }
        }
        String rail_legs = "";
        String rail_pkm = "";
        String fq_rail_pkm = "";

        String accessModes = "";
        String egressModes = "";
        String accessDistance = "";
        String egressDistance = "";
        if (railOd != null) {
            rail_pkm = calcRailPkm(trip);
            fq_rail_pkm = calcfqRailPkm(trip);
            rail_legs = Integer.toString(railTripsAnalyzer.getRailRouteSegmentsofTrip(trip).size());
            var access = findAccessMode(trip, railOd.getFirst());
            var egress = findEgressMode(trip, railOd.getSecond());
            accessModes = access.getFirst();
            accessDistance = Integer.toString(access.getSecond());
            egressModes = egress.getFirst();
            egressDistance = Integer.toString(egress.getSecond());
        }

        return List.of(fromZoneString, toZoneString, fromStation, toStation, rail_pkm, fq_rail_pkm, rail_legs, accessModes, accessDistance, egressModes, egressDistance, tourId, tripId, purpose, direction);
    }


}

