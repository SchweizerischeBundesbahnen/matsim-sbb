/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis.matrices;

import ch.sbb.matsim.routing.pt.raptor.RaptorParameters;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorCore.TravelInfo;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.opengis.feature.simple.SimpleFeature;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Calculates a zone-to-zone travel time matrix for public transport.
 *
 *  Idea of the algorithm:
 * - select n random points per zone
 * - for each point, find the possible stops to be used as departure or arrival stops.
 * - for each zone-to-zone combination, calculate the travel times from each point to each other point in the destination zone.
 *   - for each point-to-point combination, multiple connections will be available from one of the departure stops to each of the arrival stops
 *   - the algorithm selects the connection where the sum of access-time, travel-time and egress-time is the smallest.
 * - this results in n x n travel times per zone-to-zone combination.
 * - average the n x n travel times and store this value as the zone-to-zone travel time.
 *
 * A basic implementation for calculating the travel times between m zones, it would resulting m^2 * n^2 pt route calculations,
 * which could get very slow. The actual algorithm thus makes use of LeastCostPathTrees, reducing the computational effort down
 * to the calculation of m*n LeastCostPathTrees.
 *
 * @author mrieser / SBB
 */
public final class PTTravelTimeMatrix {

    private PTTravelTimeMatrix() {
    }

    public static <T> PtIndicators<T> calculateTravelTimeMatrix(SwissRailRaptor raptor, Map<T, SimpleFeature> zones, double departureTime, int numberOfPointsPerZone, RaptorParameters parameters) {
        Random r = new Random(20180404L);

        Map<T, Coord[]> coordsPerZone = new HashMap<>();
        for (Map.Entry<T, SimpleFeature> e : zones.entrySet()) {
            T zoneId = e.getKey();
            SimpleFeature f = e.getValue();
            if (f.getDefaultGeometry() != null) {
                Coord[] coords = new Coord[numberOfPointsPerZone];
                coordsPerZone.put(zoneId, coords);
                for (int i = 0; i < numberOfPointsPerZone; i++) {
                    Coord coord = Utils.getRandomCoordinateInFeature(f, r);
                    coords[i] = coord;
                }
            }
        }

        // prepare calculation
        PtIndicators<T> pti = new PtIndicators<>(zones.keySet());
        double walkSpeed = parameters.getBeelineWalkSpeed();
        float avgFactor = (float) (1.0 / numberOfPointsPerZone / numberOfPointsPerZone);

        // do calculation
        Counter cnter = new Counter("origin ", " / " + zones.size());
        for (T fromZoneId : zones.keySet()) {
            cnter.incCounter();
            Coord[] fromCoords = coordsPerZone.get(fromZoneId);
            if (fromCoords != null) {
                for (Coord fromCoord : fromCoords) {
                    Collection<TransitStopFacility> fromStops = findStopCandidates(fromCoord, raptor, parameters);
                    Map<Id<TransitStopFacility>, Double> accessTimes = new HashMap<>();
                    for (TransitStopFacility stop : fromStops) {
                        double distance = CoordUtils.calcEuclideanDistance(fromCoord, stop.getCoord());
                        double accessTime = distance / walkSpeed;
                        accessTimes.put(stop.getId(), accessTime);
                    }
                    Map<Id<TransitStopFacility>, TravelInfo> tree = raptor.calcTree(fromStops, departureTime, parameters);

                    for (T toZoneId : zones.keySet()) {
                        Coord[] toCoords = coordsPerZone.get(toZoneId);
                        if (toCoords != null) {
                            for (Coord toCoord : toCoords) {
                                Collection<TransitStopFacility> toStops = findStopCandidates(toCoord, raptor, parameters);
                                double minTotalTravelTime = Double.POSITIVE_INFINITY;
                                double minTravelTime = Double.NaN;
                                double minAccessTime = Double.NaN;
                                double minEgressTime = Double.NaN;
                                int minTransferCount = -9999;
                                for (TransitStopFacility toStop : toStops) {
                                    TravelInfo info = tree.get(toStop);
                                    if (info != null) {
                                        double accessTime = accessTimes.get(info.departureStop);
                                        double travelTime = info.travelTime;
                                        double egressDistance = CoordUtils.calcEuclideanDistance(toStop.getCoord(), toCoord);
                                        double egressTime = egressDistance / walkSpeed;
                                        double totalTravelTime = accessTime + travelTime + egressTime;
                                        if (totalTravelTime < minTotalTravelTime) {
                                            minTotalTravelTime = totalTravelTime;
                                            minTravelTime = travelTime;
                                            minAccessTime = accessTime;
                                            minEgressTime = egressTime;
                                            minTransferCount = info.transferCount;
                                        }
                                    }
                                }
                                pti.travelTimeMatrix.add(fromZoneId, toZoneId, (float) minTravelTime);
                                pti.accessTimeMatrix.add(fromZoneId, toZoneId, (float) minAccessTime);
                                pti.egressTimeMatrix.add(fromZoneId, toZoneId, (float) minEgressTime);
                                pti.transferCountMatrix.add(fromZoneId, toZoneId, (float) minTransferCount);
                            }
                        } else {
                            // this might happen if a zone has no geometry, for whatever reason...
                            pti.travelTimeMatrix.set(fromZoneId, toZoneId, Float.POSITIVE_INFINITY);
                            pti.accessTimeMatrix.set(fromZoneId, toZoneId, Float.POSITIVE_INFINITY);
                            pti.egressTimeMatrix.set(fromZoneId, toZoneId, Float.POSITIVE_INFINITY);
                            pti.transferCountMatrix.set(fromZoneId, toZoneId, Float.POSITIVE_INFINITY);
                        }
                    }
                }

            } else {
                // this might happen if a zone has no geometry, for whatever reason...
                for (T toZoneId : zones.keySet()) {
                    pti.travelTimeMatrix.set(fromZoneId, toZoneId, Float.POSITIVE_INFINITY);
                    pti.accessTimeMatrix.set(fromZoneId, toZoneId, Float.POSITIVE_INFINITY);
                    pti.egressTimeMatrix.set(fromZoneId, toZoneId, Float.POSITIVE_INFINITY);
                    pti.transferCountMatrix.set(fromZoneId, toZoneId, Float.POSITIVE_INFINITY);
                }
            }
        }

        pti.travelTimeMatrix.multiply(avgFactor);
        pti.accessTimeMatrix.multiply(avgFactor);
        pti.egressTimeMatrix.multiply(avgFactor);
        pti.transferCountMatrix.multiply(avgFactor);

        return pti;
    }

    public static class PtIndicators<T> {
        public final FloatMatrix<T> travelTimeMatrix;
        public final FloatMatrix<T> accessTimeMatrix;
        public final FloatMatrix<T> egressTimeMatrix;
        public final FloatMatrix<T> transferCountMatrix;

        public PtIndicators(Set<T> zones) {
            this.travelTimeMatrix = new FloatMatrix<>(zones, 0);
            this.accessTimeMatrix = new FloatMatrix<>(zones, 0);
            this.egressTimeMatrix = new FloatMatrix<>(zones, 0);
            this.transferCountMatrix = new FloatMatrix<>(zones, 0);
        }
    }

    private static Collection<TransitStopFacility> findStopCandidates(Coord coord, SwissRailRaptor raptor, RaptorParameters parameters) {
        Collection<TransitStopFacility> stops = raptor.getUnderlyingData().findNearbyStops(coord.getX(), coord.getY(), parameters.getSearchRadius());
        if (stops.isEmpty()) {
            TransitStopFacility nearest = raptor.getUnderlyingData().findNearestStop(coord.getX(), coord.getY());
            double nearestStopDistance = CoordUtils.calcEuclideanDistance(coord, nearest.getCoord());
            stops = raptor.getUnderlyingData().findNearbyStops(coord.getX(), coord.getY(), nearestStopDistance + parameters.getExtensionRadius());
        }
        return stops;
    }

}
