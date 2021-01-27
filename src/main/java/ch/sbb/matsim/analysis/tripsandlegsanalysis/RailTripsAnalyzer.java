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
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.router.TripStructureUtils.Trip;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

@Singleton
public class RailTripsAnalyzer {

    private final Set<Id<TransitLine>> railLines;
    private final TransitSchedule schedule;

    @Inject
    public RailTripsAnalyzer(TransitSchedule schedule) {
        this.schedule = schedule;
        railLines = schedule.getTransitLines().values()
                .stream()
                .filter(l -> l.getRoutes().values().stream().anyMatch(transitRoute -> transitRoute.getTransportMode().equals(PTSubModes.RAIL)))
                .map(transitLine -> transitLine.getId())
                .collect(Collectors.toSet());

    }

    public double calcRailDistance(Trip trip) {
        double rail_pm = trip.getLegsOnly().stream()
                .filter(leg -> leg.getRoute() instanceof TransitPassengerRoute)
                .map(leg -> (TransitPassengerRoute) leg.getRoute())
                .filter(transitRoute -> railLines.contains(transitRoute.getLineId()))
                .map(Route::getDistance)
                .mapToDouble(Double::doubleValue)
                .sum();

        return rail_pm;
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

}
