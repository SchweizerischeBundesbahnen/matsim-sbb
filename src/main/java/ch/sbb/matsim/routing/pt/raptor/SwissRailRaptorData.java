/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.routing.pt.raptor;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.utils.collections.QuadTree;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author mrieser / SBB
 */
class SwissRailRaptorData {

    private static final Logger log = Logger.getLogger(SwissRailRaptorData.class);

    final TransitSchedule schedule;
    final RaptorConfig config;
    final int countStopFacilities;
    final int countRouteStops;
    final RRoute[] routes;
    final Departure[] departures; // in the RAPTOR paper, this is usually called "trips", but I stick with the MATSim nomenclature
    final RRouteStop[] routeStops; // list of all route stops
    final RStop[] stops;
    final RTransfer[] transfers;
    final Map<TransitStopFacility, Integer> stopFacilityIndices;
    final Map<TransitStopFacility, int[]> routeStopsPerStopFacility;
    final QuadTree<TransitStopFacility> stopsQT;

    private SwissRailRaptorData(TransitSchedule schedule, RaptorConfig config,
            int countStopFacilities, RRoute[] routes, Departure[] departures, RRouteStop[] routeStops,
            RStop[] stops, /*RRouteStop[] routeStopsPerStop, */RTransfer[] transfers,
            Map<TransitStopFacility, Integer> stopFacilityIndices, Map<TransitStopFacility, int[]> routeStopsPerStopFacility, QuadTree<TransitStopFacility> stopsQT) {
        this.schedule = schedule;
        this.config = config;
        this.countStopFacilities = countStopFacilities;
        this.countRouteStops = routeStops.length;
        this.routes = routes;
        this.departures = departures;
        this.routeStops = routeStops;
        this.stops = stops;
        this.transfers = transfers;
        this.stopFacilityIndices = stopFacilityIndices;
        this.routeStopsPerStopFacility = routeStopsPerStopFacility;
        this.stopsQT = stopsQT;
    }

    public static SwissRailRaptorData create(TransitSchedule schedule, RaptorConfig config) {
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
        RRouteStop[] routeStopsPerRoute = new RRouteStop[(int) countRouteStops];

        int indexRoutes = 0;
        int indexRouteStops = 0;
        int indexDeparture = 0;

        // enumerate TransitStopFacilities along their usage in transit routes to (hopefully) achieve a better memory locality
        // well, I'm not even sure how often we'll need the transit stop facilities, likely we'll use RouteStops more often
        Map<TransitStopFacility, Integer> stopFacilityIndices = new HashMap<>((int) (countStopFacilities * 1.5));
        Map<TransitStopFacility, List<RRouteStop>> routeStopsPerStopFacility = new HashMap<>();
        Map<TransitStopFacility, int[]> routeStopsPerStopFacility2 = new HashMap<>();

        for (TransitLine line : schedule.getTransitLines().values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                RRoute rroute = new RRoute(line, route, indexRouteStops, route.getStops().size(), indexDeparture, route.getDepartures().size());
                routes[indexRoutes] = rroute;
                for (TransitRouteStop routeStop : route.getStops()) {
                    int stopFacilityIndex = stopFacilityIndices.computeIfAbsent(routeStop.getStopFacility(), stop -> stopFacilityIndices.size());
                    RRouteStop rRouteStop = new RRouteStop(routeStop, line, route, indexRoutes, stopFacilityIndex);
                    final int thisRouteStopIndex = indexRouteStops;
                    routeStopsPerRoute[thisRouteStopIndex] = rRouteStop;
                    routeStopsPerStopFacility.computeIfAbsent(routeStop.getStopFacility(), stop -> new ArrayList<>(5)).add(rRouteStop);
                    routeStopsPerStopFacility2.compute(routeStop.getStopFacility(), (stop, currentRouteStops) -> {
                        if (currentRouteStops == null) {
                            return new int[] { thisRouteStopIndex };
                        }
                        int[] tmp = new int[currentRouteStops.length + 1];
                        System.arraycopy(currentRouteStops, 0, tmp, 0, currentRouteStops.length);
                        tmp[currentRouteStops.length] = thisRouteStopIndex;
                        return tmp;
                    });
                    indexRouteStops++;
                }
                for (Departure dep : route.getDepartures().values()) {
                    departures[indexDeparture] = dep;
                    indexDeparture++;
                }
                indexRoutes++;
            }
        }

        // make sure even unused stops have an index, as they could be used during route-search
        for (TransitStopFacility stopFacility : schedule.getFacilities().values()) {
            stopFacilityIndices.computeIfAbsent(stopFacility, stop -> stopFacilityIndices.size());
        }

        Map<TransitStopFacility, RTransfer[]> allTransfers = calculateTransfers(stopsQT, stopFacilityIndices, config);
        long countTransfers = 0;
        for (RTransfer[] transfers : allTransfers.values()) {
            countTransfers += transfers.length;
        }
        if (countTransfers > Integer.MAX_VALUE) {
            throw new RuntimeException("TransitSchedule has too many Transfers: " + countTransfers);
        }
        TransitStopFacility[] stopFacilities = new TransitStopFacility[countStopFacilities];
        for (Map.Entry<TransitStopFacility, Integer> e : stopFacilityIndices.entrySet()) {
            TransitStopFacility stopFacility = e.getKey();
            int stopIndex = e.getValue();
            stopFacilities[stopIndex] = stopFacility;
        }

        indexRouteStops = 0;
        RStop[] stops = new RStop[countStopFacilities];
        RTransfer[] transfers = new RTransfer[(int) countTransfers];
        int indexTransfer = 0;
        for (int stopIndex = 0; stopIndex < countStopFacilities; stopIndex++) {
            TransitStopFacility stopFacility = stopFacilities[stopIndex];
            RTransfer[] stopTransfers = allTransfers.get(stopFacility);
            int firstTransferIndex = indexTransfer;
            int stopTransferCount = stopTransfers == null ? 0 : stopTransfers.length;
            if (stopTransferCount > 0) {
                System.arraycopy(stopTransfers, 0, transfers, indexTransfer, stopTransfers.length);
                indexTransfer += stopTransferCount;
            }
            RStop rStop = new RStop(stopFacility, firstTransferIndex, stopTransferCount/*, firstRouteStopIndex, routeStopCount*/);
            stops[stopIndex] = rStop;
        }

        SwissRailRaptorData data = new SwissRailRaptorData(schedule, config, countStopFacilities, routes, departures, routeStopsPerRoute, stops, /*routeStopsPerStop,*/ transfers, stopFacilityIndices, routeStopsPerStopFacility2, stopsQT);

        long endMillis = System.currentTimeMillis();
        log.info("SwissRailRaptor data preparation done. Took " + (endMillis - startMillis) / 1000 + " seconds.");
        log.info("SwissRailRaptor statistics:  #routes = " + routes.length);
        log.info("SwissRailRaptor statistics:  #departures = " + departures.length);
        log.info("SwissRailRaptor statistics:  #routeStops = " + routeStopsPerRoute.length);
        log.info("SwissRailRaptor statistics:  #stopFacilities = " + stops.length);
        log.info("SwissRailRaptor statistics:  #transfers (between routeStops) = " + transfers.length);
        return data;
    }

    // a very basic implementation for transfers, just connecting every transit stop facility with any other nearby,
    // including itself, as there could be other lines/routes departing at the same stop facility.
    private static Map<TransitStopFacility, RTransfer[]> calculateTransfers(QuadTree<TransitStopFacility> stopsQT, Map<TransitStopFacility, Integer> stopIndices, RaptorConfig config) {
        Map<TransitStopFacility, RTransfer[]> transfers = new HashMap<>((int) (stopsQT.size() * 1.5));
        double maxBeelineWalkConnectionDistance = config.getBeelineWalkConnectionDistance();
        double beelineWalkSpeed = config.getBeelineWalkSpeed();
        double transferUtilPerS = config.getMarginalUtilityOfTravelTimeWalk_utl_s();
        double minimalTransferTime = config.getMinimalTransferTime();

        List<RTransfer> stopTransfers = new ArrayList<>();
        for (TransitStopFacility stopFacility : stopIndices.keySet()) {
            stopTransfers.clear();
            int fromStop = stopIndices.get(stopFacility);
            Coord stopCoord = stopFacility.getCoord();
            Collection<TransitStopFacility> nearbyStops = stopsQT.getDisk(stopCoord.getX(), stopCoord.getY(), maxBeelineWalkConnectionDistance);
            for (TransitStopFacility nearbyStop : nearbyStops) {
                int toStop = stopIndices.get(nearbyStop);
                double distance = CoordUtils.calcEuclideanDistance(stopCoord, nearbyStop.getCoord());
                double transferTime = distance / beelineWalkSpeed;
                if (transferTime < minimalTransferTime) {
                    transferTime = minimalTransferTime;
                }
                double transferUtil = transferTime * transferUtilPerS;
                double transferCost = -transferUtil;
                stopTransfers.add(new RTransfer(fromStop, toStop, transferTime, transferCost));
            }
            transfers.put(stopFacility, stopTransfers.toArray(new RTransfer[stopTransfers.size()]));
        }
        return transfers;
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

    static final class RStop {
        final TransitStopFacility stopFacility;
        final int indexFirstTransfer;
        final int countTransfers;

        RStop(TransitStopFacility stopFacility, int indexFirstTransfer, int countTransfers) {
            this.stopFacility = stopFacility;
            this.indexFirstTransfer = indexFirstTransfer;
            this.countTransfers = countTransfers;
        }
    }

    static final class RRouteStop {
        final TransitRouteStop routeStop;
        final TransitLine line;
        final TransitRoute route;
        final int transitRouteIndex;
        final int stopFacilityIndex;
        final double arrivalOffset;
        final double departureOffset;

        RRouteStop(TransitRouteStop routeStop, TransitLine line, TransitRoute route, int transitRouteIndex, int stopFacilityIndex) {
            this.routeStop = routeStop;
            this.line = line;
            this.route = route;
            this.transitRouteIndex = transitRouteIndex;
            this.stopFacilityIndex = stopFacilityIndex;
            // "normalize" the arrival and departure offsets, make sure they are always well defined.
            this.arrivalOffset = isUndefinedTime(routeStop.getArrivalOffset()) ? routeStop.getDepartureOffset() : routeStop.getArrivalOffset();
            this.departureOffset = isUndefinedTime(routeStop.getDepartureOffset()) ? routeStop.getArrivalOffset() : routeStop.getDepartureOffset();
        }

        private static boolean isUndefinedTime(double time) {
            return time == Time.UNDEFINED_TIME || Double.isNaN(time);
        }
    }

    static final class RTransfer {
        final int fromStopFacility;
        final int toStopFacility;
        final double transferTime;
        final double transferCost;

        RTransfer(int fromStopFacility, int toStopFacility, double transferTime, double transferCost) {
            this.fromStopFacility = fromStopFacility;
            this.toStopFacility = toStopFacility;
            this.transferTime = transferTime;
            this.transferCost = transferCost;
        }
    }
}
