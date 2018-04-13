/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis.matrices;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.misc.Counter;
import org.matsim.core.utils.misc.Time;
import org.matsim.utils.leastcostpathtree.LeastCostPathTree;
import org.opengis.feature.simple.SimpleFeature;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Calculates a zone-to-zone travel time matrix based on network routing.
 *
 * Inspired by https://github.com/moeckel/silo/blob/siloMatsim/silo/src/main/java/edu/umd/ncsg/transportModel/Zone2ZoneTravelTimeListener.java.
 *
 * Idea of the algorithm:
 * - select n random points per zone
 * - find the nearest link and thereof the to-node for each point
 * - this results in n nodes per zone (where some nodes can appear multiple times, this is wanted as it acts as a weight/probability)
 * - for each zone-to-zone combination, calculate the travel times for each node to node combination.
 * - this results in n x n travel times per zone-to-zone combination.
 * - average the n x n travel times and store this value as the zone-to-zone travel time.
 *
 * @author mrieser / SBB
 */
public final class NetworkTravelTimeMatrix {

    private NetworkTravelTimeMatrix() {
    }

    public static <T> FloatMatrix<T> calculateTravelTimeMatrix(Network network, Map<T, SimpleFeature> zones, double departureTime, int numberOfPointsPerZone, TravelTime travelTime, TravelDisutility travelDisutility, int numberOfThreads) {
        Random r = new Random(20180404L);

        Map<T, Node[]> nodesPerZone = new HashMap<>();
        for (Map.Entry<T, SimpleFeature> e : zones.entrySet()) {
            T zoneId = e.getKey();
            SimpleFeature f = e.getValue();
            if (f.getDefaultGeometry() != null) {
                Node[] nodes = new Node[numberOfPointsPerZone];
                nodesPerZone.put(zoneId, nodes);
                for (int i = 0; i < numberOfPointsPerZone; i++) {
                    Coord coord = Utils.getRandomCoordinateInFeature(f, r);
                    Node node = NetworkUtils.getNearestLink(network, coord).getToNode();
                    nodes[i] = node;
                }
            }
        }

        // prepare calculation
        FloatMatrix<T> travelTimeMatrix = new FloatMatrix<>(zones.keySet(), 0);
        LeastCostPathTree lcpTree = new LeastCostPathTree(travelTime, travelDisutility);

        float avgFactor = (float) (1.0 / numberOfPointsPerZone / numberOfPointsPerZone);

        // do calculation
        ConcurrentLinkedQueue<T> originZones = new ConcurrentLinkedQueue<>(zones.keySet());

        Counter counter = new Counter("CAR-TravelTimeMatrix-" + Time.writeTime(departureTime) + " zone ", " / " + zones.size());
        Thread[] threads = new Thread[numberOfThreads];
        for (int i = 0; i < numberOfThreads; i++) {
            RowWorker<T> worker = new RowWorker<>(originZones, zones.keySet(), network, nodesPerZone, travelTimeMatrix, departureTime, travelTime, travelDisutility, counter);
            threads[i] = new Thread(worker, "CAR-TravelTimeMatrix-" + Time.writeTime(departureTime) + "-" + i);
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

        travelTimeMatrix.multiply(avgFactor);

        return travelTimeMatrix;
    }

    public static class RowWorker<T> implements Runnable {
        private final ConcurrentLinkedQueue<T> originZones;
        private final Set<T> destinationZones;
        private final Network network;
        private final Map<T, Node[]> nodesPerZone;
        private final FloatMatrix<T> travelTimeMatrix;
        private final TravelTime travelTime;
        private final TravelDisutility travelDisutility;
        private final double departureTime;
        private final Counter counter;

        RowWorker(ConcurrentLinkedQueue<T> originZones, Set<T> destinationZones, Network network, Map<T, Node[]> nodesPerZone, FloatMatrix<T> travelTimeMatrix, double departureTime, TravelTime travelTime, TravelDisutility travelDisutility, Counter counter) {
            this.originZones = originZones;
            this.destinationZones = destinationZones;
            this.network = network;
            this.nodesPerZone = nodesPerZone;
            this.travelTimeMatrix = travelTimeMatrix;
            this.departureTime = departureTime;
            this.travelTime = travelTime;
            this.travelDisutility = travelDisutility;
            this.counter = counter;
        }

        public void run() {
            LeastCostPathTree lcpTree = new LeastCostPathTree(this.travelTime, this.travelDisutility);
            while (true) {
                T fromZoneId = this.originZones.poll();
                if (fromZoneId == null) {
                    return;
                }

                this.counter.incCounter();
                Node[] fromNodes = this.nodesPerZone.get(fromZoneId);
                if (fromNodes != null) {
                    for (Node fromNode : fromNodes) {
                        lcpTree.calculate(this.network, fromNode, this.departureTime);

                        for (T toZoneId : this.destinationZones) {
                            Node[] toNodes = this.nodesPerZone.get(toZoneId);
                            if (toNodes != null) {
                                for (Node toNode : toNodes) {
                                    double tt = lcpTree.getTree().get(toNode.getId()).getTime() - this.departureTime;
                                    this.travelTimeMatrix.add(fromZoneId, toZoneId, (float) tt);
                                }
                            } else {
                                // this might happen if a zone has no geometry, for whatever reason...
                                this.travelTimeMatrix.set(fromZoneId, toZoneId, Float.POSITIVE_INFINITY);
                            }
                        }
                    }
                } else {
                    // this might happen if a zone has no geometry, for whatever reason...
                    for (T toZoneId : this.destinationZones) {
                        this.travelTimeMatrix.set(fromZoneId, toZoneId, Float.POSITIVE_INFINITY);
                    }
                }
            }
        }
    }

}
