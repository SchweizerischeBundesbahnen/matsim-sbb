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

package ch.sbb.matsim.mavi.streets;

import ch.sbb.matsim.config.variables.Filenames;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.mavi.PolylinesCreator;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.NetworkConfigGroup;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.filter.NetworkFilterManager;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

import static ch.sbb.matsim.mavi.streets.VisumStreetNetworkExporter.fullModeset;
import static ch.sbb.matsim.mavi.streets.VisumStreetNetworkExporter.modeSetWithoutBike;

public class StreetNetworkExporter {

    public static void main(String[] args) throws IOException {
        final StreetsExporterConfigGroup streetsExporterConfigGroup = new StreetsExporterConfigGroup();
        Config config = ConfigUtils.loadConfig(args[0], streetsExporterConfigGroup);
        Zones zones = ZonesLoader.loadZones("1", streetsExporterConfigGroup.getZonesFile());
        String outputDir = streetsExporterConfigGroup.getOutputDirURL(config.getContext()).getPath();
        new File(outputDir).mkdirs();
        VisumStreetNetworkExporter vse = new VisumStreetNetworkExporter();
        String visumFile = streetsExporterConfigGroup.getVisumFileURL(config.getContext()).getPath()
                .replace("////", "//")
                .replace("/", "\\");
        vse.run(visumFile, outputDir, Integer.parseInt(streetsExporterConfigGroup.getVisumVersion()),
                streetsExporterConfigGroup.isExportCounts(), true);
        Network network = vse.getNetwork();
        if (streetsExporterConfigGroup.getSmallRoadSpeedFactor() < 1.0 || streetsExporterConfigGroup.getMainRoadSpeedFactor() < 1.0) {
            ReduceNetworkSpeeds reduceNetworkSpeeds = new ReduceNetworkSpeeds(network, zones, streetsExporterConfigGroup.getSmallRoadSpeedFactor(),
                    streetsExporterConfigGroup.getMainRoadSpeedFactor());
            reduceNetworkSpeeds.reduceSpeeds();
        }

        if (streetsExporterConfigGroup.isReduceForeignLinks()) {
            LogManager.getLogger(StreetNetworkExporter.class).info("Removing foreign rural links.");
            RemoveForeignRuralLinks r = new RemoveForeignRuralLinks(network, zones);
            r.removeLinks();
            NetworkCleaner cleaner = new NetworkCleaner();
            cleaner.run(network);
        }

        if (streetsExporterConfigGroup.isMergeRuralLinks()) {
            LogManager.getLogger(StreetNetworkExporter.class).info("Merging rural links.");
            MergeRuralLinks l = new MergeRuralLinks(network, zones);
            l.mergeRuralLinks();
            NetworkCleaner cleaner = new NetworkCleaner();
            cleaner.run(network);
        }
        adjustRoundaboutLinks(network);

        filterBikeNetwork(network, outputDir);
        finalMultimodalCleanup(network);
        assureLinkLenghtsAndSpeedsAreSet(network);

        LogManager.getLogger(StreetNetworkExporter.class).info("Writing Network with polylines.");
        new NetworkWriter(network).write(outputDir + "/" + Filenames.STREET_NETWORK_WITH_POLYLINES);
        removePolylines(network);

        LogManager.getLogger(StreetNetworkExporter.class).info("Writing Network without polylines.");
        new NetworkWriter(network).write(outputDir + "/" + Filenames.STREET_NETWORK);

    }

    private static void assureLinkLenghtsAndSpeedsAreSet(Network network) {

        for (Link link : network.getLinks().values()){
            double beelineDistance = CoordUtils.calcEuclideanDistance(link.getFromNode().getCoord(),link.getToNode().getCoord());
            if (link.getLength()==0.0 || link.getLength()<beelineDistance){
                link.setLength(Math.max(0.1,beelineDistance));
            }
        }
    }

    private static void finalMultimodalCleanup(Network network) {
        NetworkFilterManager fm = new NetworkFilterManager(network, new NetworkConfigGroup());
        fm.addLinkFilter(l -> l.getAllowedModes().contains(SBBModes.BIKE));
        Network bikenet = fm.applyFilters();
        new NetworkCleaner().run(bikenet);

        NetworkFilterManager fm2 = new NetworkFilterManager(network, new NetworkConfigGroup());
        fm2.addLinkFilter(l -> l.getAllowedModes().contains(SBBModes.CAR));
        Network carnet = fm2.applyFilters();
        new NetworkCleaner().run(carnet);

        for (Link link : network.getLinks().values()) {
            if (link.getAllowedModes().contains(SBBModes.BIKE)) {
                if (!bikenet.getLinks().containsKey(link.getId())) {
                    Set<String> allowedModes = new HashSet<>(link.getAllowedModes());
                    allowedModes.remove(SBBModes.BIKE);
                    link.setAllowedModes(allowedModes);
                }
            }
            if (link.getAllowedModes().contains(SBBModes.CAR)) {
                if (!carnet.getLinks().containsKey(link.getId())) {
                    Set<String> allowedModes = new HashSet<>(link.getAllowedModes());
                    allowedModes.remove(SBBModes.CAR);
                    allowedModes.remove(SBBModes.RIDE);
                    link.setAllowedModes(allowedModes);
                }
            }
        }
        Set<Link> toRemove = network.getLinks().values().stream().filter(link -> link.getAllowedModes().isEmpty()).collect(Collectors.toSet());
        LogManager.getLogger(StreetNetworkExporter.class).info("Removing " + toRemove.size() + " links in multimodal cleanup.");
        toRemove.forEach(link -> network.removeLink(link.getId()));
        Set<Node> nodesToRemove = network.getNodes().values().stream().filter(node -> node.getOutLinks().size() == 0 && node.getInLinks().size() == 0).collect(Collectors.toSet());
        LogManager.getLogger(StreetNetworkExporter.class).info("Removing " + nodesToRemove.size() + " nodes in multimodal cleanup.");

        nodesToRemove.forEach(node -> network.removeNode(node.getId()));

        network.getLinks().values().stream().filter(l -> (!String.valueOf(l.getAttributes().getAttribute(Variables.ACCESS_CONTROLLED)).equals("1"))).filter(l -> l.getAllowedModes().size() < 3).forEach(link -> link.getAttributes().putAttribute(Variables.ACCESS_CONTROLLED, 0));

    }


    private static void filterBikeNetwork(Network network, String outputDir) {
        NetworkFilterManager fm = new NetworkFilterManager(network, new NetworkConfigGroup());
        fm.addLinkFilter(l -> l.getAllowedModes().contains(SBBModes.BIKE));
        Network bikenet = fm.applyFilters();
        Set<Id<Link>> needsBikeBackLink = new HashSet<>();
        Random r = MatsimRandom.getRandom();
        for (Link link : bikenet.getLinks().values()) {
            if (NetworkUtils.findLinkInOppositeDirection(link) == null) {
                needsBikeBackLink.add(link.getId());
            }
        }

        for (var linkId : needsBikeBackLink) {
            Link link = bikenet.getLinks().get(linkId);
            Link carNetLink = network.getLinks().get(linkId);
            Tuple<Integer, Integer> visumLinkAndNodeId = VisumStreetNetworkExporter.extractVisumNodeAndLinkId(linkId);
            Integer visumLinkId = visumLinkAndNodeId != null ? visumLinkAndNodeId.getSecond() : null;
            if (visumLinkId == null) {
                visumLinkId = Integer.MAX_VALUE - r.nextInt(5000);
            }
            Id<Link> backLinkId = VisumStreetNetworkExporter.createLinkId(link.getToNode().getId().toString().split("_")[1], Integer.toString(visumLinkId));
            Link bikeBackLink = bikenet.getFactory().createLink(backLinkId, link.getToNode(), link.getFromNode());
            bikeBackLink.setLength(link.getLength());
            bikeBackLink.setAllowedModes(Set.of(SBBModes.BIKE));
            bikeBackLink.setFreespeed(link.getFreespeed());
            bikeBackLink.setNumberOfLanes(link.getNumberOfLanes());
            bikeBackLink.setCapacity(link.getCapacity());
            bikeBackLink.getAttributes().putAttribute(Variables.ACCESS_CONTROLLED, 1);
            bikenet.addLink(bikeBackLink);


            Link carNetBacklink = NetworkUtils.findLinkInOppositeDirection(carNetLink);
            if (carNetBacklink == null) {
                carNetBacklink = network.getFactory().createLink(backLinkId, carNetLink.getToNode(), carNetLink.getFromNode());
                carNetBacklink.setLength(link.getLength());
                carNetBacklink.setAllowedModes(Set.of(SBBModes.BIKE));
                carNetBacklink.setFreespeed(link.getFreespeed());
                carNetBacklink.setNumberOfLanes(link.getNumberOfLanes());
                carNetBacklink.setCapacity(link.getCapacity());
                carNetBacklink.getAttributes().putAttribute(Variables.ACCESS_CONTROLLED, 1);
                network.addLink(carNetBacklink);
            } else {
                network.getLinks().get(backLinkId).setAllowedModes(fullModeset);
            }

        }

        org.matsim.core.network.algorithms.NetworkCleaner cleaner = new org.matsim.core.network.algorithms.NetworkCleaner();
        cleaner.run(bikenet);


        List<Link> deadEnds = bikenet.getLinks().values().stream().filter(link -> link.getToNode().getOutLinks().size() == 0 || link.getFromNode().getInLinks().size() == 0).collect(Collectors.toList());

        while (!deadEnds.isEmpty()) {
            for (var link : deadEnds) {
                bikenet.removeLink(link.getId());
            }
            deadEnds = bikenet.getLinks().values().stream().filter(link -> link.getToNode().getOutLinks().size() == 0 || link.getFromNode().getInLinks().size() == 0).collect(Collectors.toList());

        }
        for (Link l : network.getLinks().values()) {
            if (!bikenet.getLinks().containsKey(l.getId())) {
                l.setAllowedModes(modeSetWithoutBike);
                l.getAttributes().putAttribute(Variables.ACCESS_CONTROLLED, 1);

            }
        }


    }

    public static void adjustRoundaboutLinks(Network network) {
        LogManager.getLogger(StreetNetworkExporter.class).info("Adjusting capacities of roundabouts.");
        int i = 0;
        Set<Id<Link>> adjustedLinks = new HashSet<>();
        for (Link l : network.getLinks().values()) {
            if (l.getLength() > 27) {
                continue;
            }
            if (l.getCapacity() > 1500) {
                continue;
            }

            if (l.getToNode().getOutLinks().size() < 3) {
                if ((l.getToNode().getOutLinks().size() == 1) && (l.getToNode().getInLinks().size() == 1)) {
                    continue;
                }
                boolean hasbacklink = false;
                for (Link ol : l.getToNode().getOutLinks().values()) {
                    if (ol.getToNode() == l.getFromNode()) {
                        hasbacklink = true;
                    }
                }
                if (!hasbacklink) {
                    l.setCapacity(1500);
                    adjustedLinks.add(l.getId());
                    i++;
                }
            }

        }
        LogManager.getLogger(StreetNetworkExporter.class).info("Adjusted  " + i + " link capacities in roundabouts.");
    }

    private static void removePolylines(Network network) {
        network.getLinks().values().forEach(l -> l.getAttributes().removeAttribute(PolylinesCreator.WKT_ATTRIBUTE));
    }
}
