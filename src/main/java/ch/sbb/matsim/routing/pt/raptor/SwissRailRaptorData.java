/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.routing.pt.raptor;

import org.apache.log4j.Logger;
import org.matsim.core.config.Config;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.HashMap;
import java.util.Map;

/**
 * @author mrieser / SBB
 */
class SwissRailRaptorData {

    private static final Logger log = Logger.getLogger(SwissRailRaptorData.class);

    final int countStopFacilities;
    final RRoute[] routes;
    final Departure[] departures;
    final TransitRouteStop[] routeStops;
    final Map<TransitStopFacility, Integer> stopFacilityIndices;
    final QuadTree<TransitStopFacility> stopsQT;

    private SwissRailRaptorData(int countStopFacilities, RRoute[] routes, Departure[] departures, TransitRouteStop[] routeStops, Map<TransitStopFacility, Integer> stopFacilityIndices, QuadTree<TransitStopFacility> stopsQT) {
        this.countStopFacilities = countStopFacilities;
        this.routes = routes;
        this.departures = departures;
        this.routeStops = routeStops;
        this.stopFacilityIndices = stopFacilityIndices;
        this.stopsQT = stopsQT;
    }

    public static SwissRailRaptorData create(TransitSchedule schedule, Config config) {
        log.info("Preparing data for SwissRailRaptor...");
        long startMillis = System.currentTimeMillis();

        int countStopFacilities = schedule.getFacilities().size();

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (TransitStopFacility stopFacility : schedule.getFacilities().values()) {
            double x = stopFacility.getCoord().getX();
            double y = stopFacility.getCoord().getY();

            if (x < minX) minX = x;
            if (y < minY) minY = y;
            if (x > maxX) maxX = x;
            if (y > maxY) maxY = y;
        }
        QuadTree<TransitStopFacility> stopsQT = new QuadTree<>(minX, minY, maxX, maxY);
        for (TransitStopFacility stopFacility : schedule.getFacilities().values()) {
            double x = stopFacility.getCoord().getX();
            double y = stopFacility.getCoord().getY();
            stopsQT.put(x, y, stopFacility);
        }

        Map<TransitStopFacility, Integer> stopFacilityIndices = new HashMap<>((int) (countStopFacilities * 1.5));
        int countRoutes = 0;
        long countRouteStops = 0;
        long countDepartures = 0;

        for (TransitLine line : schedule.getTransitLines().values()) {
            countRoutes += line.getRoutes().size();
            for (TransitRoute route : line.getRoutes().values()) {
                countRouteStops += route.getStops().size();
                countDepartures += route.getDepartures().size();
            }
        }

        if (countRouteStops > Integer.MAX_VALUE) {
            throw new RuntimeException("TransitSchedule has too many TransitRouteStops: " + countRouteStops);
        }
        if (countDepartures > Integer.MAX_VALUE) {
            throw new RuntimeException("TransitSchedule has too many Departures: " + countDepartures);
        }

        Departure[] departures = new Departure[(int) countDepartures];
        RRoute[] routes = new RRoute[countRoutes];
        TransitRouteStop[] routeStops = new TransitRouteStop[(int) countRouteStops];

        int indexRoutes = 0;
        int indexRouteStops = 0;
        int indexDeparture = 0;

        for (TransitLine line : schedule.getTransitLines().values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                routes[indexRoutes] = new RRoute(line, route, indexRouteStops, route.getStops().size(), indexDeparture, route.getDepartures().size());
                for (TransitRouteStop routeStop : route.getStops()) {
                    routeStops[indexRouteStops] = routeStop;
                    indexRouteStops++;
                    stopFacilityIndices.computeIfAbsent(routeStop.getStopFacility(), stop -> stopFacilityIndices.size());
                }
                for (Departure dep : route.getDepartures().values()) {
                    departures[indexDeparture] = dep;
                    indexDeparture++;
                }
                indexRoutes++;
            }
        }

        SwissRailRaptorData data = new SwissRailRaptorData(countStopFacilities, routes, departures, routeStops, stopFacilityIndices, stopsQT);

        long endMillis = System.currentTimeMillis();
        log.info("SwissRailRaptor data preparation done. Took " + (endMillis - startMillis) / 1000 + " seconds.");
        return data;
    }

    static final class RRoute {
        final TransitLine line;
        final TransitRoute route;
        final int indexFirstRouteStop;
        final int countRouteStops;
        final int indexFirstDeparture;
        final int countDepartures;

        RRoute(TransitLine line, TransitRoute route, int indexFirstRouteStop, int countRouteStops, int indexFirstDeparture, int countDepartures) {
            this.line = line;
            this.route = route;
            this.indexFirstRouteStop = indexFirstRouteStop;
            this.countRouteStops = countRouteStops;
            this.indexFirstDeparture = indexFirstDeparture;
            this.countDepartures = countDepartures;
        }
    }
}
