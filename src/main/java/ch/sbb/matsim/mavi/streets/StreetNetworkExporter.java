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
import ch.sbb.matsim.mavi.visum.Visum;
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
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;
import org.matsim.core.network.algorithms.NetworkSimplifier;
import org.matsim.core.network.filter.NetworkFilterManager;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordUtils;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static ch.sbb.matsim.mavi.streets.VisumStreetNetworkExporter.*;

public class StreetNetworkExporter {

    public static final String VISUM_IDS = "visumIds";

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
        if (visumFile.contains(":")) {
            visumFile = visumFile.substring(1);
        }
        Visum visum = new Visum(Integer.parseInt(streetsExporterConfigGroup.getVisumVersion()));
        visum.loadVersion(visumFile);

        vse.run(visum, outputDir, streetsExporterConfigGroup.isExportCounts(), true);
        Network network = vse.getNetwork();

        removeIrrelevantLinks(network, zones);
        setAccessControlled(network);
        setModes(network, zones);

        if (streetsExporterConfigGroup.getSmallRoadSpeedFactor() < 1.0 || streetsExporterConfigGroup.getMainRoadSpeedFactor() < 1.0) {
            ReduceNetworkSpeeds reduceNetworkSpeeds = new ReduceNetworkSpeeds(network, zones, streetsExporterConfigGroup.getSmallRoadSpeedFactor(),
                    streetsExporterConfigGroup.getMainRoadSpeedFactor());
            reduceNetworkSpeeds.reduceSpeeds();
        }
        adjustRoundaboutLinks(network);
        if (streetsExporterConfigGroup.isSimplifyNetwork()) {
            simplifyNetwork(network, vse.getLinksWithCountingStations());
        }
        finalMultimodalCleanup(network);
        assureLinkLenghtsAndSpeedsAreSet(network);

        LogManager.getLogger(StreetNetworkExporter.class).info("Writing Network with polylines.");
        new NetworkWriter(network).write(outputDir + "/" + Filenames.STREET_NETWORK_WITH_POLYLINES);
        removePolylines(network);

        LogManager.getLogger(StreetNetworkExporter.class).info("Writing Network without polylines.");
        new NetworkWriter(network).write(outputDir + "/" + Filenames.STREET_NETWORK);

    }


    private static void simplifyNetwork(Network network, Set<Id<Link>> linksWithCountingStations) {
        NetworkSimplifier simplifier = new NetworkSimplifier();
        simplifier.setMergeLinkStats(true);
        simplifier.run(network, ((link, link2) -> link.getAllowedModes().equals(link2.getAllowedModes()) && !linksWithCountingStations.contains(link.getId()) && !linksWithCountingStations.contains(link2.getId())), (linkLinkTuple, link) -> {
            String newPolyLine = mergeWKTPolygons(linkLinkTuple);
            if (newPolyLine != null) link.getAttributes().putAttribute(PolylinesCreator.WKT_ATTRIBUTE, newPolyLine);
            int accessControlled = Math.min(1, (int) linkLinkTuple.getFirst().getAttributes().getAttribute(Variables.ACCESS_CONTROLLED) + (int) linkLinkTuple.getSecond().getAttributes().getAttribute(Variables.ACCESS_CONTROLLED));
            link.getAttributes().putAttribute(Variables.ACCESS_CONTROLLED, accessControlled);
            NetworkUtils.setType(link, NetworkUtils.getType(linkLinkTuple.getFirst()));
            link.setAllowedModes(linkLinkTuple.getFirst().getAllowedModes());
            link.setCapacity(Math.max(linkLinkTuple.getFirst().getCapacity(), linkLinkTuple.getSecond().getCapacity()));
            link.setNumberOfLanes(Math.max(linkLinkTuple.getFirst().getNumberOfLanes(), linkLinkTuple.getSecond().getNumberOfLanes()));

        });
    }

    private static String mergeWKTPolygons(Tuple<Link, Link> linksToMerge) {
        StringBuilder wkt = new StringBuilder(128);
        wkt.append("LINESTRING(");
        boolean hasAtleastOneWKT = false;
        String wkt1 = (String) linksToMerge.getFirst().getAttributes().getAttribute(PolylinesCreator.WKT_ATTRIBUTE);
        String wkt2 = (String) linksToMerge.getSecond().getAttributes().getAttribute(PolylinesCreator.WKT_ATTRIBUTE);
        if (wkt1 != null) {
            wkt.append(wkt1, 11, wkt1.length() - 1);
            wkt.append(",");

            hasAtleastOneWKT = true;
        }
        if (wkt2 != null) {
            wkt.append(wkt2, 11, wkt2.length() - 1);
            hasAtleastOneWKT = true;
        }
        wkt.append(')');
        return hasAtleastOneWKT ? wkt.toString() : null;
    }

    private static void removeIrrelevantLinks(Network network, Zones zones) {
        Set<Link> linksForRemoval = network.getLinks().values().stream()
                .filter(link -> (boolean) link.getAttributes().getAttribute(MOBI_REMOVAL_CANDIDATE))
                .filter(link -> {
                    var zone = zones.findZone(link.getCoord());
                    if (zone == null) return true;
                    else {
                        return !zone.getAttribute("sl3_id").toString().equals("1");
                    }
                })
                .collect(Collectors.toSet());
        linksForRemoval.forEach(link -> network.removeLink(link.getId()));
        LogManager.getLogger(StreetNetworkExporter.class).info("Removed " + linksForRemoval.size() + " links that were flagged irrelevant and are in non-urban areas.");

    }

    private static void setModes(Network network, Zones zones) {
        Set<Id<Link>> linksForRemoval = new HashSet<>();
        for (Link link : network.getLinks().values()) {
            int accessControlled = (int) link.getAttributes().getAttribute(Variables.ACCESS_CONTROLLED);
            if (accessControlled == 0) {
                if (link.getAllowedModes().contains(SBBModes.CAR)) {
                    link.setAllowedModes(fullModeset);
                }
            }
            if (accessControlled == 1) {
                if (link.getAllowedModes().contains(SBBModes.CAR)) {
                    int typeNo = Integer.parseInt(NetworkUtils.getType(link));
                    if (typeNo > 96 && typeNo < 100) {
                        //ferries and trains should be bikeable.
                        link.setAllowedModes(fullModeset);
                    } else link.setAllowedModes(modeSetWithoutBike);

                }
            }

        }
        for (Link link : network.getLinks().values()) {
            if (link.getAllowedModes().isEmpty()) {
                Link backLink = NetworkUtils.findLinkInOppositeDirection(link);
                if (backLink != null) {
                    if (backLink.getAllowedModes().contains(SBBModes.BIKE) && backLink.getFreespeed() <= 30 / 3.6) {
                        link.setAllowedModes(modeSetBikeOnly);
                        link.setCapacity(600);
                        link.setFreespeed(backLink.getFreespeed());

                    } else if (backLink.getAllowedModes().isEmpty()) {
                        var zone = zones.findZone(link.getCoord());
                        if (zone != null) {
                            if (zone.getAttribute("sl3_id").toString().equals("1")) {
                                //these are urban roads that are closed in NPVM because they are open for local access only
                                link.setFreespeed(15 / 3.6);
                                link.setAllowedModes(fullModeset);
                                link.setCapacity(300);
                                link.getAttributes().putAttribute(Variables.ACCESS_CONTROLLED, 0);
                                backLink.setFreespeed(15 / 3.6);
                                backLink.setAllowedModes(fullModeset);
                                backLink.setCapacity(300);
                                backLink.getAttributes().putAttribute(Variables.ACCESS_CONTROLLED, 0);
                            }

                        }
                    } else {
                        linksForRemoval.add(link.getId());
                    }
                } else {
                    linksForRemoval.add(link.getId());
                }

            }
        }
        linksForRemoval.forEach(network::removeLink);

    }

    private static void setAccessControlled(Network network) {
        Set<Integer> accessControlledTypes = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            accessControlledTypes.add(i);
        }
        for (int i = 62; i < 78; i++) {
            accessControlledTypes.add(i);
        }
        for (int i = 97; i < 100; i++) {
            accessControlledTypes.add(i);
        }
        network.getLinks()
                .values()
                .forEach(link -> {
                    int accessControlled = accessControlledTypes.contains(Integer.parseInt(NetworkUtils.getType(link))) ? 1 : 0;
                    link.getAttributes().putAttribute(Variables.ACCESS_CONTROLLED, accessControlled);
                });

    }

    private static void assureLinkLenghtsAndSpeedsAreSet(Network network) {
        int adjustedLinks = 0;
        for (Link link : network.getLinks().values()){
            double beelineDistance = CoordUtils.calcEuclideanDistance(link.getFromNode().getCoord(),link.getToNode().getCoord());
            boolean adjusted = false;
            if (link.getLength()==0.0 || link.getLength()<beelineDistance){
                link.setLength(Math.max(0.1,beelineDistance));
                adjusted = true;
            }
            if (link.getFreespeed() <= 0.1) {
                link.setFreespeed(15 / 3.6);
                adjusted = true;

            }
            if (link.getNumberOfLanes() < 1) {
                link.setNumberOfLanes(1.0);
                adjusted = true;
            }
            if (link.getCapacity() < 300) {
                link.setCapacity(300);
                adjusted = true;
            }
            if (adjusted) adjustedLinks++;
        }
        LogManager.getLogger(StreetNetworkExporter.class).info("Adjusted lengths, capacities, lanes or freespeed for " + adjustedLinks + " links.");
    }

    public static void finalMultimodalCleanup(Network network) {
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
        Set<Node> nodesToRemove = network.getNodes().values().stream().filter(node -> node.getOutLinks().isEmpty() && node.getInLinks().isEmpty()).collect(Collectors.toSet());
        LogManager.getLogger(StreetNetworkExporter.class).info("Removing " + nodesToRemove.size() + " nodes in multimodal cleanup.");

        nodesToRemove.forEach(node -> network.removeNode(node.getId()));

        network.getLinks().values().stream()
                .filter(l -> l.getAllowedModes().size() < 3)
                .forEach(link -> link.getAttributes().putAttribute(Variables.ACCESS_CONTROLLED, 1));

    }


    public static void adjustRoundaboutLinks(Network network) {
        LogManager.getLogger(StreetNetworkExporter.class).info("Adjusting capacities of roundabouts.");
        int i = 0;
        for (Link l : network.getLinks().values()) {
            if (!l.getAllowedModes().contains(SBBModes.CAR)) {
                continue;
            }

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
                        if (ol.getAllowedModes().contains(SBBModes.CAR)) {
                            hasbacklink = true;
                        }
                    }
                }
                if (!hasbacklink) {
                    l.setCapacity(1500);
                    i++;
                }
            }

        }
        LogManager.getLogger(StreetNetworkExporter.class).info("Adjusted  " + i + " link capacities in roundabouts.");
    }

    public static void removePolylines(Network network) {
        network.getLinks().values().forEach(l -> l.getAttributes().removeAttribute(PolylinesCreator.WKT_ATTRIBUTE));
    }
}
