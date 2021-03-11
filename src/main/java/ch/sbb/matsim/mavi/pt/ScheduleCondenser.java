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

package ch.sbb.matsim.mavi.pt;

import ch.sbb.matsim.csv.CSVReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;

public class ScheduleCondenser {

    public static final Logger LOGGER = Logger.getLogger(ScheduleCondenser.class);
    private final Map<Id<Link>, String> linkToVisumSequence;
    private final TransitSchedule transitSchedule;
    private final Network network;

    public ScheduleCondenser(Map<Id<Link>, String> linkToVisumSequence, TransitSchedule schedule, Network network) {
        this.linkToVisumSequence = linkToVisumSequence;
        this.transitSchedule = schedule;
        this.network = network;
    }

    public static void main(String[] args) throws IOException {
        String inputSchedule = args[0];
        String outputSchedule = args[1];
        String inputNetwork = args[2];
        String outputNetwork = args[3];
        String segmentFile = args[4];
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(inputSchedule);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(inputNetwork);
        Map<Id<Link>, String> sequencePerMatsimLink = new HashMap<>();
        try (CSVReader in = new CSVReader(segmentFile, ";")) {
            Map<String, String> row;
            while ((row = in.readLine()) != null) {
                String matsimLinkId = row.get("matsim_link");
                String linkSequence = row.get("link_sequence_visum");
                sequencePerMatsimLink.put(Id.createLinkId(matsimLinkId), linkSequence);
            }
        }
        new ScheduleCondenser(sequencePerMatsimLink, scenario.getTransitSchedule(), scenario.getNetwork()).condenseSchedule();
        cleanNetwork(scenario.getTransitSchedule(), scenario.getNetwork());

        new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(outputSchedule);
        new NetworkWriter(scenario.getNetwork()).write(outputNetwork);
    }

    public static void cleanNetwork(TransitSchedule schedule, Network network) {
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
        LOGGER.info("Removed " + linksToRemove.size() + " unused links.");

        Set<Id<Node>> nodesToRemove = network.getNodes().values().stream().
                filter(n -> n.getInLinks().size() == 0 && n.getOutLinks().size() == 0).
                map(Node::getId).
                collect(Collectors.toSet());
        nodesToRemove.forEach(network::removeNode);
        LOGGER.info("removed " + nodesToRemove.size() + " unused nodes.");
        for (Link l : network.getLinks().values()) {
            double beelineLength = CoordUtils.calcEuclideanDistance(l.getFromNode().getCoord(), l.getToNode().getCoord());
            if (l.getLength() < beelineLength) {
                if (beelineLength - l.getLength() > 1.0) {
                    LOGGER.warn(l.getId() + " has a length (" + l.getLength() + ") shorter than its beeline distance (" + beelineLength + "). Correcting this.");
                }
                l.setLength(beelineLength);
            }
            if (l.getLength() <= 0.0) {
                l.setLength(0.01);
            }
        }
    }

    public void condenseSchedule() {
        //sort links by length
        List<Link> linksLongestToShortest = network.getLinks().values().stream().collect(Collectors.toList());
        Collections.sort(linksLongestToShortest, (l1, l2) -> Double.compare(l2.getLength(), l1.getLength()));
        Iterator<Link> links = linksLongestToShortest.iterator();
        while (links.hasNext()) {
            Link link = links.next();
            List<String> originalSequence = new ArrayList<>(Arrays.asList(CollectionUtils.stringToArray(linkToVisumSequence.get(link.getId()))));
            List<Link> replacement = new ArrayList<>();
            var lastFromNode = link.getFromNode();
            List<Node> visitedNodes = new ArrayList<>();
            int currentSegmentIndex = 0;
            do {
                visitedNodes.add(lastFromNode);
                Link l = getNextPuzzlePiece(lastFromNode, originalSequence, link, visitedNodes, currentSegmentIndex);
                if (l != null) {
                    lastFromNode = l.getToNode();
                    List<String> linkSequence = Arrays.asList(CollectionUtils.stringToArray(linkToVisumSequence.get(l.getId())));
                    String lastSegment = linkSequence.get(linkSequence.size() - 1);
                    currentSegmentIndex = originalSequence.lastIndexOf(lastSegment);
                    if (currentSegmentIndex == -1) {
                        throw new RuntimeException();
                    }

                    replacement.add(l);
                    if (l.getToNode().equals(link.getToNode())) {
                        break;
                    }
                } else {
                    break;
                }
            } while (true);
            if (replacement.size() > 0 && replacement.get(replacement.size() - 1).getToNode().equals(link.getToNode())) {
                replaceInSchedule(link, replacement);
            }
            links.remove();
        }
        cleanNetwork(this.transitSchedule, this.network);
        condenseSimilarLinks(linksLongestToShortest);

    }

    private void condenseSimilarLinks(List<Link> linksLongestToShortest) {
        List<Link> toRemove = new ArrayList<>();
        for (Link l : linksLongestToShortest) {
            if (toRemove.contains(l)) {
                continue;
            }
            for (var ol : l.getFromNode().getOutLinks().values()) {
                if (ol != l) {
                    if (ol.getToNode() == l.getToNode()) {
                        toRemove.add(ol);
                        replaceInSchedule(ol, List.of(l));
                    }
                }
            }
        }
        linksLongestToShortest.removeAll(toRemove);

    }

    private void replaceInSchedule(Link link, List<Link> replacement) {
        transitSchedule.getTransitLines().values().stream().flatMap(transitLine -> transitLine.getRoutes().values().stream()).forEach(transitRoute -> {
            final NetworkRoute networkRoute = transitRoute.getRoute();
            if (networkRoute.getLinkIds().contains(link.getId())) {
                List<Id<Link>> newRoute = new ArrayList<>();
                for (var linkId : networkRoute.getLinkIds()) {
                    if (linkId.equals(link.getId())) {
                        newRoute.addAll(replacement.stream().map(l -> l.getId()).collect(Collectors.toList()));

                    } else {
                        newRoute.add(linkId);
                    }
                }
                networkRoute.setLinkIds(networkRoute.getStartLinkId(), newRoute, networkRoute.getEndLinkId());
            }
        });
    }

    private Link getNextPuzzlePiece(Node expandNode, List<String> originalLinkSequence, Link originalLink, List<Node> visitedNodes, int currentSegmentIndex) {
        int longestPuzzlePiece = 0;
        Link candidate = null;
        for (Link linkcandidate : expandNode.getOutLinks().values()) {
            if (linkcandidate.getFromNode().equals(originalLink.getFromNode()) && linkcandidate.getToNode().equals(originalLink.getToNode())) {
                continue;
            }
            if (visitedNodes.contains(linkcandidate.getToNode())) {
                continue;
            }
            List<String> linkSequence = Arrays.asList(CollectionUtils.stringToArray(linkToVisumSequence.get(linkcandidate.getId())));
            if (linkSequence.size() > 0 && linkSequence.size() <= originalLinkSequence.size() - currentSegmentIndex) {
                int i = 0;
                String nextSection = linkSequence.get(0);
                int deviation = 0;
                if (originalLinkSequence.size() > currentSegmentIndex + 1 && nextSection.equals(originalLinkSequence.get(currentSegmentIndex + 1))) {
                    deviation++;
                }

                while (originalLinkSequence.size() > (currentSegmentIndex + i + deviation) && nextSection.equals(originalLinkSequence.get(currentSegmentIndex + i + deviation))) {
                    i++;
                    if (i < linkSequence.size()) {
                        nextSection = linkSequence.get(i);
                    } else {
                        break;
                    }

                }
                if (i == linkSequence.size() && i > longestPuzzlePiece) {
                    longestPuzzlePiece = i;
                    candidate = linkcandidate;
                }
            }
        }
        return candidate;
    }
}
