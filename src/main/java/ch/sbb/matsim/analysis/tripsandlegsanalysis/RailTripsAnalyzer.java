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

import ch.sbb.matsim.config.variables.SBBModes.PTSubModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.routing.access.AccessEgressModule;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.*;
import java.util.stream.Collectors;

@Singleton
public class RailTripsAnalyzer {

    private final Set<Id<TransitLine>> railLines;
    private final Set<Id<TransitStopFacility>> fqStops;
    private final Set<Id<TransitStopFacility>> swissRailStops;
    private final Set<Id<TransitStopFacility>> railStops;
    private final TransitSchedule schedule;
    private final Network network;

    @Inject
    public RailTripsAnalyzer(TransitSchedule schedule, Network network, ZonesCollection zonesCollection) {
        var zones = zonesCollection.getZones(Id.create("zones", Zones.class));
        this.network = network;
        this.schedule = schedule;
        railLines = schedule.getTransitLines().values()
                .stream()
                .filter(l -> l.getRoutes().values().stream().anyMatch(transitRoute -> transitRoute.getTransportMode().equals(PTSubModes.RAIL)))
                .map(Identifiable::getId)
                .collect(Collectors.toSet());
        fqStops = schedule.getFacilities().values()
                .stream()
                .filter(transitStopFacility -> String.valueOf(transitStopFacility.getAttributes().getAttribute(Variables.FQ_RELEVANT)).equals("1"))
                .map(Identifiable::getId)
                .collect(Collectors.toSet());
        railStops = railLines.stream()
                .flatMap(lid -> schedule.getTransitLines().get(lid).getRoutes().values().stream())
                .filter(transitRoute -> transitRoute.getTransportMode().equals(PTSubModes.RAIL))
                .flatMap(transitRoute -> transitRoute.getStops().stream())
                .map(transitRouteStop -> transitRouteStop.getStopFacility().getId())
                .collect(Collectors.toSet());

        swissRailStops = railStops.stream()
                .map(id -> schedule.getFacilities().get(id))
                .filter(stop -> {
                    Zone z = zones.findZone(stop.getCoord());
                    if (z == null) return false;
                    else return AccessEgressModule.isSwissZone(z.getId());
                })
                .map(Identifiable::getId)
                .collect(Collectors.toSet());
        //Mostly Domodossola, as there are swiss domestic trips routed along here
        swissRailStops.addAll(Variables.EXCEPTIONAL_CH_STOPS);
    }

    public boolean isRailLine(Id<TransitLine> transitLineId) {
        return railLines.contains(transitLineId);
    }

    public double calcRailDistance(Trip trip) {

        return getRailRouteSegmentsofTrip(trip).stream()
                .map(Route::getDistance)
                .mapToDouble(Double::doubleValue)
                .sum();
    }

    public List<TransitPassengerRoute> getRailRouteSegmentsofTrip(Trip trip) {
        return trip.getLegsOnly().stream()
                .filter(leg -> leg.getRoute() instanceof TransitPassengerRoute)
                .map(leg -> (TransitPassengerRoute) leg.getRoute())
                .filter(transitRoute -> railLines.contains(transitRoute.getLineId()))
                .collect(Collectors.toList());
    }

    public Map<Id<TransitLine>, Set<Id<TransitRoute>>> getTransitLinesAndRoutesAtStop(Id<TransitStopFacility> stopId) {
        Map<Id<TransitLine>, Set<Id<TransitRoute>>> result = new HashMap<>();
        for (TransitLine line : schedule.getTransitLines().values()) {
            Set<Id<TransitRoute>> routeAtFacilty = line.getRoutes().values()
                    .stream()
                    .filter(transitRoute -> transitRoute.getStops()
                            .stream()
                            .map(stop -> stop.getStopFacility().getId())
                            .anyMatch(stopFacilityId -> stopFacilityId.equals(stopId)))
                    .map(TransitRoute::getId)
                    .collect(Collectors.toSet());
            if (!routeAtFacilty.isEmpty()) {
                result.put(line.getId(), routeAtFacilty);
            }
        }
        return result;
    }

    /**
     * Finds the Access and egress Railway Stop of a trip
     *
     * @param trip
     * @return Tuple with access and egress railway stop.
     */
    public Tuple<Id<TransitStopFacility>, Id<TransitStopFacility>> getOriginDestination(Trip trip) {
        Tuple<Id<TransitStopFacility>, Id<TransitStopFacility>> tuple = null;
        Id<TransitStopFacility> firstStop = null;
        Id<TransitStopFacility> lastStop = null;
        for (Leg leg : trip.getLegsOnly()) {
            if (leg.getRoute() instanceof TransitPassengerRoute) {
                TransitPassengerRoute route = (TransitPassengerRoute) leg.getRoute();
                if (railLines.contains(route.getLineId())) {
                    if (firstStop == null) {
                        firstStop = route.getAccessStopId();
                    }
                    lastStop = route.getEgressStopId();
                }
            }
        }
        if (firstStop != null && lastStop != null) {
            tuple = new Tuple<>(firstStop, lastStop);
        }

        return tuple;
    }

    public RailTravelInfo getRailTravelInfo(Trip trip) {
        Id<TransitStopFacility> firstStop = null;
        Id<TransitStopFacility> lastStop = null;
        double railDistance = 0.;
        double travelTime = 0.;
        int numberOfTransfers = 0;
        boolean lastLegWasRail = false;
        for (Leg leg : trip.getLegsOnly()) {
            if (leg.getRoute() instanceof TransitPassengerRoute) {
                TransitPassengerRoute route = (TransitPassengerRoute) leg.getRoute();
                if (railLines.contains(route.getLineId())) {
                    if (lastLegWasRail) {
                        numberOfTransfers++;
                    }
                    lastLegWasRail = true;
                } else {
                    lastLegWasRail = false;
                }

                if (railLines.contains(route.getLineId())) {
                    if (firstStop == null) {
                        firstStop = route.getAccessStopId();

                    }
                    lastStop = route.getEgressStopId();
                    railDistance += route.getDistance();
                    travelTime += route.getTravelTime().seconds();
                }
            }
        }
        if (firstStop != null && lastStop != null) {
            return new RailTravelInfo(firstStop, lastStop, travelTime, railDistance, numberOfTransfers);
        } else return null;
    }

    public record RailTravelInfo(Id<TransitStopFacility> fromStation, Id<TransitStopFacility> toStation,
                                 double railTravelTime, double distance, int numberOfTransfers) {
    }


    /**
     * A rail leg between SIMBA/CH-Perimeter stops or to&from Domodossola, or the swiss section of an international rail leg
     */
    public double getDomesticRailDistance_m(TransitPassengerRoute transitPassengerRoute) {
        var accessStopId = transitPassengerRoute.getAccessStopId();
        var egressStopId = transitPassengerRoute.getEgressStopId();
        final boolean accesIsSwissRailStop = isSwissRailStop(accessStopId);
        final boolean egressIsSwissRailStop = isSwissRailStop(egressStopId);
        if (accesIsSwissRailStop && egressIsSwissRailStop) {
            return transitPassengerRoute.getDistance();
        } else if (accesIsSwissRailStop) {
            calcDomesticDistanceToBorder(transitPassengerRoute.getRouteId(), transitPassengerRoute.getLineId(), accessStopId);
            return calcDomesticDistanceToBorder(transitPassengerRoute.getRouteId(), transitPassengerRoute.getLineId(), accessStopId);
        } else if (egressIsSwissRailStop) {
            return calcDomesticDistanceFromBorder(transitPassengerRoute.getRouteId(), transitPassengerRoute.getLineId(), egressStopId);
        } else {
            //entirely international or non-rail pt leg
            return calcSwissSectionofInternationalJourney(transitPassengerRoute.getRouteId(), transitPassengerRoute.getLineId(), accessStopId, egressStopId);
        }
    }

    /**
     * @param routeId
     * @param transitLineId
     * @param accessStopId
     * @param egressStopId
     * @return the swiss section of a train passing through Switzerland, e.g. Frankfurt-Milano
     */
    private double calcSwissSectionofInternationalJourney(Id<TransitRoute> routeId, Id<TransitLine> transitLineId, Id<TransitStopFacility> accessStopId, Id<TransitStopFacility> egressStopId) {
        TransitRoute transitRoute = this.schedule.getTransitLines().get(transitLineId).getRoutes().get(routeId);
        Gbl.assertNotNull(transitRoute);
        if (!transitRoute.getTransportMode().equals(PTSubModes.RAIL)) {
            return 0.0;
        }
        int startIndex = transitRoute.getStops().stream().map(s -> s.getStopFacility().getId()).collect(Collectors.toList()).indexOf(accessStopId);
        TransitStopFacility lastDomesticStop = null;
        for (int i = startIndex + 1; i < transitRoute.getStops().size(); i++) {
            var currentStop = transitRoute.getStops().get(i);
            if (isSwissRailStop(currentStop.getStopFacility().getId())) {
                lastDomesticStop = currentStop.getStopFacility();
            }

        }

        int endIndex = transitRoute.getStops().stream().map(s -> s.getStopFacility().getId()).collect(Collectors.toList()).indexOf(egressStopId);
        TransitStopFacility firstDomesticStop = null;
        for (int i = 0; i < endIndex; i++) {
            var currentStop = transitRoute.getStops().get(i);
            if (isSwissRailStop(currentStop.getStopFacility().getId())) {
                firstDomesticStop = currentStop.getStopFacility();
            }

        }
        if (firstDomesticStop != null && lastDomesticStop != null) {
            return RouteUtils.calcDistance(transitRoute, firstDomesticStop, lastDomesticStop, this.network);
        } else {
            return 0.0;
        }

    }

    private double calcDomesticDistanceToBorder(Id<TransitRoute> routeId, Id<TransitLine> transitLineId, Id<TransitStopFacility> accessStopId) {
        TransitRoute transitRoute = this.schedule.getTransitLines().get(transitLineId).getRoutes().get(routeId);
        Gbl.assertNotNull(transitRoute);
        int startIndex = transitRoute.getStops().stream().map(s -> s.getStopFacility().getId()).collect(Collectors.toList()).indexOf(accessStopId);
        final TransitStopFacility accessStop = schedule.getFacilities().get(accessStopId);
        TransitStopFacility lastDomesticStop = accessStop;
        for (int i = startIndex + 1; i < transitRoute.getStops().size(); i++) {
            var currentStop = transitRoute.getStops().get(i);
            if (isSwissRailStop(currentStop.getStopFacility().getId())) {
                lastDomesticStop = currentStop.getStopFacility();
            }

        }
        return RouteUtils.calcDistance(transitRoute, accessStop, lastDomesticStop, this.network);
    }

    private double calcDomesticDistanceFromBorder(Id<TransitRoute> routeId, Id<TransitLine> transitLineId, Id<TransitStopFacility> egressStopId) {
        TransitRoute transitRoute = this.schedule.getTransitLines().get(transitLineId).getRoutes().get(routeId);
        Gbl.assertNotNull(transitRoute);
        int endIndex = transitRoute.getStops().stream().map(s -> s.getStopFacility().getId()).collect(Collectors.toList()).indexOf(egressStopId);
        final TransitStopFacility egressStop = schedule.getFacilities().get(egressStopId);
        TransitStopFacility firstDomesticStop = null;
        for (int i = 0; i < endIndex; i++) {
            var currentStop = transitRoute.getStops().get(i);
            if (isSwissRailStop(currentStop.getStopFacility().getId())) {
                firstDomesticStop = currentStop.getStopFacility();
            }

        }
        if (firstDomesticStop != null) {
            return RouteUtils.calcDistance(transitRoute, firstDomesticStop, egressStop, this.network);
        } else {
            return 0.0;
        }
    }

    public boolean isSwissRailStop(Id<TransitStopFacility> stopId) {
        return swissRailStops.contains(stopId);
    }

    public boolean isSwissRailOrFQStop(Id<TransitStopFacility> stopId) {
        if (isSwissRailStop(stopId)) {
            return true;
        }
        //some fq stops are not in CH: SBB GmbH
        else {
            return fqStops.contains(stopId);
        }
    }

    //calculates according to
    //https://confluence.sbb.ch/display/MOBIMANUAL/10.2.1+Filterung+in+MATSim

    public double getFQDistance(Trip trip, boolean includeForeignTrips) {
        List<TransitPassengerRoute> routes = getRailRouteSegmentsofTrip(trip);
        return getFQDistance(routes, includeForeignTrips);
    }

    public double getFQDistance(List<TransitPassengerRoute> routes, boolean includeForeignTrips) {

        if (routes.isEmpty()) {
            return 0.0;
        }
        Id<TransitStopFacility> railAccessStop = routes.get(0).getAccessStopId();
        Id<TransitStopFacility> railEgressStop = routes.get(routes.size() - 1).getEgressStopId();
        if (isSwissRailStop(railAccessStop) && isSwissRailStop(railEgressStop)) {
            boolean hasFQRelevantLeg = hasFQRelevantLeg(routes);
            if (hasFQRelevantLeg) {
                return routes.stream().mapToDouble(Route::getDistance).sum();
            }
        } else if (includeForeignTrips && (isSwissRailOrFQStop(railAccessStop) || isSwissRailOrFQStop(railEgressStop))) {
            // no need to check whether legs are fq relevant, as all rail border crossing train stations are FQ relevant, thus is the journey
            return routes.stream().mapToDouble(this::getDomesticRailDistance_m).sum();
        }
        return 0.0;
    }

    public boolean hasFQRelevantLeg(List<TransitPassengerRoute> routes) {
        return routes.stream().anyMatch(route -> (fqStops.contains(route.getAccessStopId()) && fqStops.contains(route.getEgressStopId())));
    }

    public List<Id<Link>> getPtLinkIdsTraveledOnExludingAccessEgressStop(TransitPassengerRoute route) {
        var result = getPtLinkIdsTraveledOn(route);
        if (result.isEmpty()) return result;
        return result.subList(1, result.size() - 1);
    }

    public List<Id<Link>> getPtLinkIdsTraveledOn(TransitPassengerRoute route) {
        TransitRoute transitRoute = this.schedule.getTransitLines().get(route.getLineId()).getRoutes().get(route.getRouteId());
        Gbl.assertNotNull(transitRoute);
        NetworkRoute nr = transitRoute.getRoute();

        Id<Link> enterLinkId = schedule.getFacilities().get(route.getAccessStopId()).getLinkId();
        Id<Link> exitLinkId = schedule.getFacilities().get(route.getEgressStopId()).getLinkId();
        List<Id<Link>> linkList = new ArrayList<>();
        boolean count = false;
        if (enterLinkId.equals(nr.getStartLinkId())) {
            count = true;
            linkList.add(nr.getStartLinkId());
        }
        for (Id<Link> linkId : nr.getLinkIds()) {
            if (count) {
                linkList.add(linkId);
            }
            if (enterLinkId.equals(linkId)) {
                count = true;
                linkList.add(linkId);

            }
            if (exitLinkId.equals(linkId)) {
                count = false;
                break;
            }
        }
        if (count) {
            linkList.add(nr.getEndLinkId());
        }
        return linkList;
    }

}
