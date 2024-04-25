package ch.sbb.matsim.mavi;

import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.*;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public class MaviHelper {
    public static Id<Link> createLinkId(String fromNode, String visumLinkId) {
        return Id.createLinkId(Integer.toString(Integer.parseInt(fromNode), 36) + "_" + Integer.toString(Integer.parseInt(visumLinkId), 36));
    }

    public static Id<Link> createPtLinkId(String fromNode, String visumLinkId) {
        return Id.createLinkId(Integer.toString(Integer.parseInt(fromNode), 36) + "_" + Integer.toString(Integer.parseInt(visumLinkId), 36) + "_pt");
    }

    public static Tuple<Integer, Integer> extractVisumNodeAndLinkId(Id<Link> linkId) {
        return extractVisumNodeAndLinkId(linkId.toString());
    }

    public static Tuple<Integer, Integer> extractVisumNodeAndLinkId(String linkIdString) {
        if (linkIdString.contains("merged")) return null;
        try {
            int visumFromNodeId = Integer.parseInt(linkIdString.split("_")[0], 36);
            int visumLinkId = Integer.parseInt(linkIdString.split("_")[1], 36);
            return Tuple.of(visumFromNodeId, visumLinkId);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public static Id<Node> createPtNodeId(String stopNodeIDNo) {
        return Id.createNodeId(stopNodeIDNo + "_pt");
    }


    public static void removeUnusedLinksInPTNetwork(TransitSchedule schedule, Network network) {
        Set<Id<Link>> linksToKeep = new HashSet<>();

        for (TransitLine line : schedule.getTransitLines().values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                linksToKeep.add(route.getRoute().getStartLinkId());
                linksToKeep.add(route.getRoute().getEndLinkId());
                linksToKeep.addAll(route.getRoute().getLinkIds());
            }
        }
        Set<Id<Link>> linksToRemove = network.getLinks().keySet().stream().
                filter(linkId -> !linksToKeep.contains(linkId)).
                collect(Collectors.toSet());
        linksToRemove.forEach(network::removeLink);
        LogManager.getLogger(MaviHelper.class).info("Removed " + linksToRemove.size() + " unused links.");

        Set<Id<Node>> nodesToRemove = network.getNodes().values().stream().
                filter(n -> n.getInLinks().isEmpty() && n.getOutLinks().isEmpty()).
                map(Node::getId).
                collect(Collectors.toSet());
        nodesToRemove.forEach(network::removeNode);
        LogManager.getLogger(MaviHelper.class).info("removed " + nodesToRemove.size() + " unused nodes.");
        for (Link l : network.getLinks().values()) {
            double beelineLength = CoordUtils.calcEuclideanDistance(l.getFromNode().getCoord(), l.getToNode().getCoord());
            if (l.getLength() < beelineLength) {
                if (beelineLength - l.getLength() > 1.0) {
                    LogManager.getLogger(MaviHelper.class).warn(l.getId() + " has a length (" + l.getLength() + ") shorter than its beeline distance (" + beelineLength + "). Correcting this.");
                }
                l.setLength(beelineLength);
            }
            if (l.getLength() <= 0.0) {
                l.setLength(0.01);
            }
        }
    }

    public static void cleanStops(TransitSchedule schedule) {
        Set<Id<TransitStopFacility>> stopsToKeep = new HashSet<>();
        for (TransitLine line : schedule.getTransitLines().values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                for (TransitRouteStop stops : route.getStops()) {
                    if (stops != null) {
                        if (stops.getStopFacility() != null) {
                            stopsToKeep.add(stops.getStopFacility().getId());
                        } else {
                            LogManager.getLogger(MaviHelper.class).warn("A stop facility on route " + route.getId() + "on line" + line.getId() + " is null");
                        }
                    } else {
                        LogManager.getLogger(MaviHelper.class).warn("stop is null");
                    }
                }
            }
        }
        Set<Id<TransitStopFacility>> stopsToRemove = schedule.getFacilities().keySet().stream().
                filter(stopId -> !stopsToKeep.contains(stopId)).
                collect(Collectors.toSet());
        stopsToRemove.forEach(stopId -> schedule.removeStopFacility(schedule.getFacilities().get(stopId)));
        LogManager.getLogger(MaviHelper.class).info("removed " + stopsToRemove.size() + " unused stop facilities.");

        MinimalTransferTimes mtt = schedule.getMinimalTransferTimes();
        MinimalTransferTimes.MinimalTransferTimesIterator itr = mtt.iterator();
        while (itr.hasNext()) {
            itr.next();
            if (!stopsToKeep.contains(itr.getFromStopId()) || !stopsToKeep.contains(itr.getToStopId())) {
                mtt.remove(itr.getFromStopId(), itr.getToStopId());
            }
        }
    }

}
