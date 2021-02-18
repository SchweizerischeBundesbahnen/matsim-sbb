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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

@Singleton
public class RailTripsAnalyzer {

    private final Set<Id<TransitLine>> railLines;
    private final Set<Id<TransitStopFacility>> fqStops;
    private final TransitSchedule schedule;
    private final Network network;

    @Inject
    public RailTripsAnalyzer(TransitSchedule schedule, Network network) {
        this.network = network;
        this.schedule = schedule;
        railLines = schedule.getTransitLines().values()
                .stream()
                .filter(l -> l.getRoutes().values().stream().anyMatch(transitRoute -> transitRoute.getTransportMode().equals(PTSubModes.RAIL)))
                .map(transitLine -> transitLine.getId())
                .collect(Collectors.toSet());
        fqStops = schedule.getFacilities().values()
                .stream()
                .filter(transitStopFacility -> String.valueOf(transitStopFacility.getAttributes().getAttribute(Variables.FQ_RELEVANT)).equals("1"))
                .map(transitStopFacility -> transitStopFacility.getId())
                .collect(Collectors.toSet());
    }

    public boolean isRailLine(Id<TransitLine> transitLineId) {
        return railLines.contains(transitLineId);
    }

    public double calcRailDistance(Trip trip) {
        double rail_pm = getRailRouteSegmentsofTrip(trip).stream()
                .map(Route::getDistance)
                .mapToDouble(Double::doubleValue)
                .sum();

        return rail_pm;
    }

    public List<TransitPassengerRoute> getRailRouteSegmentsofTrip(Trip trip) {
        return trip.getLegsOnly().stream()
                .filter(leg -> leg.getRoute() instanceof TransitPassengerRoute)
                .map(leg -> (TransitPassengerRoute) leg.getRoute())
                .filter(transitRoute -> railLines.contains(transitRoute.getLineId()))
                .collect(Collectors.toList());
    }

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
            return 0;
        }
    }

    private double calcDomesticDistanceToBorder(Id<TransitRoute> routeId, Id<TransitLine> transitLineId, Id<TransitStopFacility> accessStopId) {
        TransitRoute transitRoute = this.schedule.getTransitLines().get(transitLineId).getRoutes().get(routeId);
        Gbl.assertNotNull(transitRoute);
        int startIndex = transitRoute.getStops().indexOf(accessStopId);
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
        int endIndex = transitRoute.getStops().indexOf(egressStopId);
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
        if (Variables.EXCEPTIONAL_CH_STOPS.contains(stopId)) {
            return true;
        }
        var stopFacility = this.schedule.getFacilities().get(stopId);
        Gbl.assertNotNull(stopFacility);
        var perim = String.valueOf(stopFacility.getAttributes().getAttribute(Variables.SIMBA_CH_PERIMETER));
        if (perim.equals("1")) {
            return true;
        } else {
            return false;
        }
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

    public double getFQDistance(Trip trip) {
        List<TransitPassengerRoute> routes = getRailRouteSegmentsofTrip(trip);
        if (routes.isEmpty()) {
            return 0.0;
        }
        Id<TransitStopFacility> railAccessStop = routes.get(0).getAccessStopId();
        Id<TransitStopFacility> railEgressStop = routes.get(routes.size() - 1).getEgressStopId();
        if (isSwissRailOrFQStop(railAccessStop) && isSwissRailOrFQStop(railEgressStop)) {
            boolean hasFQRelevantLeg = routes.stream().anyMatch(route -> (fqStops.contains(route.getAccessStopId()) && fqStops.contains(route.getEgressStopId())));
            if (hasFQRelevantLeg) {
                return routes.stream().mapToDouble(route -> route.getDistance()).sum();
            }
        }
        return 0.0;
    }
}
