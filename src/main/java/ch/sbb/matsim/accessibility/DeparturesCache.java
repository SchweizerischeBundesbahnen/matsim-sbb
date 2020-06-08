package ch.sbb.matsim.accessibility;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.misc.OptionalTime;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

class DeparturesCache {

    Map<Id<TransitStopFacility>, double[]> cache;

    public DeparturesCache(TransitSchedule schedule) {
        Map<Id<TransitStopFacility>, ArrayList<Double>> tmpCache = new ConcurrentHashMap<>(schedule.getFacilities().size());

        for (TransitLine line : schedule.getTransitLines().values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                for (TransitRouteStop routeStop : route.getStops()) {
                    TransitStopFacility stop = routeStop.getStopFacility();
                    OptionalTime offset = routeStop.getDepartureOffset();
                    if (offset.isDefined()) {
                        Id<TransitStopFacility> stopId = stop.getId();
                        ArrayList<Double> departures = tmpCache.computeIfAbsent(stopId, k -> new ArrayList<>());
                        for (Departure dep : route.getDepartures().values()) {
                            departures.add(dep.getDepartureTime() + offset.seconds());
                        }
                    }
                }
            }
        }

        this.cache = new ConcurrentHashMap<>(schedule.getFacilities().size());
        tmpCache.forEach((stopId, departureTimes) -> {
            double[] departures = departureTimes.stream().mapToDouble(Double::doubleValue).toArray();
            Arrays.sort(departures);
            cache.put(stopId, departures);
        });
    }

    OptionalTime getNextDepartureTime(Id<TransitStopFacility> stopId, double depTime) {
        double[] departures = this.cache.get(stopId);
        if (departures == null) {
            return OptionalTime.undefined();
        }
        int idx = Arrays.binarySearch(departures, depTime);
        if (idx < 0) {
            // idx = (-insertionpoint) - 1
            // idx + 1 = -insertionpoint
            // -(idx + 1) = insertionpoint
            idx = -(idx + 1);
        }
        if (idx >= departures.length) {
            return OptionalTime.undefined();
        }
        return OptionalTime.defined(departures[idx]);
    }
}
