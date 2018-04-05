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
import org.matsim.utils.leastcostpathtree.LeastCostPathTree;
import org.opengis.feature.simple.SimpleFeature;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

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

    public static <T> FloatMatrix<T> calculateTravelTimeMatrix(Network network, Map<T, SimpleFeature> zones, double departureTime, int numberOfPointsPerZone, TravelTime travelTime, TravelDisutility travelDisutility) {
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
        Counter cnter = new Counter("origin ", " / " + zones.size());
        for (T fromZoneId : zones.keySet()) {
            cnter.incCounter();
            Node[] fromNodes = nodesPerZone.get(fromZoneId);
            if (fromNodes != null) {
                for (Node fromNode : fromNodes) {
                    lcpTree.calculate(network, fromNode, departureTime);

                    for (T toZoneId : zones.keySet()) {
                        Node[] toNodes = nodesPerZone.get(toZoneId);
                        if (toNodes != null) {
                            for (Node toNode : toNodes) {
                                double tt = lcpTree.getTree().get(toNode.getId()).getTime() - departureTime;
                                travelTimeMatrix.add(fromZoneId, toZoneId, (float) tt);
                            }
                        } else {
                            // this might happen if a zone has no geometry, for whatever reason...
                            travelTimeMatrix.set(fromZoneId, toZoneId, Float.POSITIVE_INFINITY);
                        }
                    }
                }
            } else {
                // this might happen if a zone has no geometry, for whatever reason...
                for (T toZoneId : zones.keySet()) {
                    travelTimeMatrix.set(fromZoneId, toZoneId, Float.POSITIVE_INFINITY);
                }
            }
        }

        travelTimeMatrix.multiply(avgFactor);

        return travelTimeMatrix;
    }

}
