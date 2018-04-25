/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis.matrices;

import ch.sbb.matsim.routing.pt.raptor.RaptorParameters;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorCore.TravelInfo;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.core.utils.misc.Time;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.opengis.feature.simple.SimpleFeature;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

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
 * to the calculation of m*n LeastCostPathTrees. In addition, it supports running the calculation in parallel to reduce the time
 * required to compute one matrix.
 *
 * @author mrieser / SBB
 */
public final class PTTravelTimeMatrix {

    private PTTravelTimeMatrix() {
    }

    public static <T> PtIndicators<T> calculateTravelTimeMatrix(SwissRailRaptorData raptorData, Map<T, SimpleFeature> zones, double departureTime, int numberOfPointsPerZone, RaptorParameters parameters, int numberOfThreads) {
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
//        float avgFactor = (float) (1.0 / numberOfPointsPerZone / numberOfPointsPerZone);

        // do calculation
        ConcurrentLinkedQueue<T> originZones = new ConcurrentLinkedQueue<>(zones.keySet());

        Counter counter = new Counter("PT-TravelTimeMatrix-" + Time.writeTime(departureTime) + " zone ", " / " + zones.size());
        Thread[] threads = new Thread[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            SwissRailRaptor raptor = new SwissRailRaptor(raptorData, null, null);
            RowWorker<T> worker = new RowWorker<>(originZones, zones.keySet(), coordsPerZone, pti, raptor, parameters, departureTime, counter);
            threads[i] = new Thread(worker, "PT-TravelTimeMatrix-" + Time.writeTime(departureTime) + "-" + i);
            threads[i].start();
        }

        // wait until all threads have finished
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        for (T fromZoneId : zones.keySet()) {
            for (T toZoneId : zones.keySet()) {
                float count = pti.dataCountMatrix.get(fromZoneId, toZoneId);
                float avgFactor = 1.0f / count;
                pti.travelTimeMatrix.multiply(fromZoneId, toZoneId, avgFactor);
                pti.accessTimeMatrix.multiply(fromZoneId, toZoneId, avgFactor);
                pti.egressTimeMatrix.multiply(fromZoneId, toZoneId, avgFactor);
                pti.transferCountMatrix.multiply(fromZoneId, toZoneId, avgFactor);
            }
        }

        return pti;
    }

    public static class RowWorker<T> implements Runnable {
        private final ConcurrentLinkedQueue<T> originZones;
        private final Set<T> destinationZones;
        private final Map<T, Coord[]> coordsPerZone;
        private final PtIndicators<T> pti;
        private final SwissRailRaptor raptor;
        private final RaptorParameters parameters;
        private final double departureTime;
        private final Counter counter;

        RowWorker(ConcurrentLinkedQueue<T> originZones, Set<T> destinationZones, Map<T, Coord[]> coordsPerZone, PtIndicators<T> pti, SwissRailRaptor raptor, RaptorParameters parameters, double departureTime, Counter counter) {
            this.originZones = originZones;
            this.destinationZones = destinationZones;
            this.coordsPerZone = coordsPerZone;
            this.pti = pti;
            this.raptor = raptor;
            this.parameters = parameters;
            this.departureTime = departureTime;
            this.counter = counter;
        }

        public void run() {
            double walkSpeed = this.parameters.getBeelineWalkSpeed();

            while (true) {
                T fromZoneId = this.originZones.poll();
                if (fromZoneId == null) {
                    return;
                }

                this.counter.incCounter();
                Coord[] fromCoords = this.coordsPerZone.get(fromZoneId);
                if (fromCoords != null) {
                    for (Coord fromCoord : fromCoords) {
                        Collection<TransitStopFacility> fromStops = findStopCandidates(fromCoord, this.raptor, this.parameters);
                        Map<Id<TransitStopFacility>, Double> accessTimes = new HashMap<>();
                        for (TransitStopFacility stop : fromStops) {
                            double distance = CoordUtils.calcEuclideanDistance(fromCoord, stop.getCoord());
                            double accessTime = distance / walkSpeed;
                            accessTimes.put(stop.getId(), accessTime);
                        }
                        Map<Id<TransitStopFacility>, TravelInfo> tree = this.raptor.calcTree(fromStops, this.departureTime, this.parameters);

                        for (T toZoneId : this.destinationZones) {
                            Coord[] toCoords = this.coordsPerZone.get(toZoneId);
                            if (toCoords != null) {
                                for (Coord toCoord : toCoords) {
                                    Collection<TransitStopFacility> toStops = findStopCandidates(toCoord, this.raptor, this.parameters);
                                    double minTotalTravelTime = Double.POSITIVE_INFINITY;
                                    double minTravelTime = Double.NaN;
                                    double minAccessTime = Double.NaN;
                                    double minEgressTime = Double.NaN;
                                    int minTransferCount = -9999;
                                    for (TransitStopFacility toStop : toStops) {
                                        TravelInfo info = tree.get(toStop.getId());
                                        if (info != null) { // it might be that some stops are not reachable
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
                                    if (minTransferCount >= 0) {
                                        this.pti.travelTimeMatrix.add(fromZoneId, toZoneId, (float) minTravelTime);
                                        this.pti.accessTimeMatrix.add(fromZoneId, toZoneId, (float) minAccessTime);
                                        this.pti.egressTimeMatrix.add(fromZoneId, toZoneId, (float) minEgressTime);
                                        this.pti.transferCountMatrix.add(fromZoneId, toZoneId, (float) minTransferCount);
                                        this.pti.dataCountMatrix.add(fromZoneId, toZoneId, 1);
                                    }
                                }
                            } else {
                                // this might happen if a zone has no geometry, for whatever reason...
                                this.pti.travelTimeMatrix.set(fromZoneId, toZoneId, Float.POSITIVE_INFINITY);
                                this.pti.accessTimeMatrix.set(fromZoneId, toZoneId, Float.POSITIVE_INFINITY);
                                this.pti.egressTimeMatrix.set(fromZoneId, toZoneId, Float.POSITIVE_INFINITY);
                                this.pti.transferCountMatrix.set(fromZoneId, toZoneId, Float.POSITIVE_INFINITY);
                            }
                        }
                    }

                } else {
                    // this might happen if a zone has no geometry, for whatever reason...
                    for (T toZoneId : this.destinationZones) {
                        this.pti.travelTimeMatrix.set(fromZoneId, toZoneId, Float.POSITIVE_INFINITY);
                        this.pti.accessTimeMatrix.set(fromZoneId, toZoneId, Float.POSITIVE_INFINITY);
                        this.pti.egressTimeMatrix.set(fromZoneId, toZoneId, Float.POSITIVE_INFINITY);
                        this.pti.transferCountMatrix.set(fromZoneId, toZoneId, Float.POSITIVE_INFINITY);
                    }
                }


            }
        }
    }

    public static class PtIndicators<T> {
        public final FloatMatrix<T> travelTimeMatrix;
        public final FloatMatrix<T> accessTimeMatrix;
        public final FloatMatrix<T> egressTimeMatrix;
        public final FloatMatrix<T> transferCountMatrix;
        public final FloatMatrix<T> dataCountMatrix; // how many values/routes were taken into account to calculate the averages

        public PtIndicators(Set<T> zones) {
            this.travelTimeMatrix = new FloatMatrix<>(zones, 0);
            this.accessTimeMatrix = new FloatMatrix<>(zones, 0);
            this.egressTimeMatrix = new FloatMatrix<>(zones, 0);
            this.transferCountMatrix = new FloatMatrix<>(zones, 0);
            this.dataCountMatrix = new FloatMatrix<>(zones, 0);
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
