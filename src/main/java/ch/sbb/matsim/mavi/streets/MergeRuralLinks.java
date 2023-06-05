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

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.mavi.PolylinesCreator;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.CoordUtils;

import java.util.*;
import java.util.stream.Collectors;

public class MergeRuralLinks {

    public static final String VNODES = "vnodes";
    final Zones zones;

    private final Network network;
    private int merged = 0;
    private final Map<Id<Link>, Link> linksToAdd = new HashMap<>();
    private final List<Link> linksToRemove = new ArrayList<>();

    public MergeRuralLinks(Network network, Zones zones) {
        this.network = network;
        this.zones = zones;
    }

    public void mergeRuralLinks() {

        for (Node node : network.getNodes().values()) {

            if (node.getOutLinks().size() < 2) {
                continue;
            }

            Zone zone = zones.findZone(node.getCoord());
            boolean rural = true;
            if (zone != null) {
                if (zone.getAttribute("sl3_id").toString().equals("1")) {
                    rural = false;
                }
            }

            if (rural) {
                for (Link l : node.getOutLinks().values()) {
                    Link nextLink = l;
                    List<Link> linksToMerge = new ArrayList<>();
                    linksToMerge.add(l);
                    while (nextLink.getToNode().getOutLinks().size() < 3) {
                        Link outLink = findRealOutLink(nextLink);
                        if (outLink != null) {
                            if (nextLink.getNumberOfLanes() != outLink.getNumberOfLanes()) {
                                linksToMerge.clear();
                                break;
                            }
                            linksToMerge.add(outLink);
                            nextLink = outLink;

                        } else {
                            linksToMerge.clear();
                            break;
                        }
                    }
                    if (linksToMerge.size() > 1) {
                        mergeLinks(linksToMerge);
                    }
                }

            }


        }
        for (Link l : linksToRemove) {
            network.removeLink(l.getId());
        }
        for (Link l : linksToAdd.values()) {
            network.addLink(l);
        }
        System.out.println(linksToRemove.size() + " removed");
        System.out.println(linksToAdd.size() + " added");


    }

    private void mergeLinks(List<Link> linksToMerge) {
        Link link0 = linksToMerge.get(0);
        double lanes = link0.getNumberOfLanes();
        Node fromNode = link0.getFromNode();
        Node toNode = linksToMerge.get(linksToMerge.size() - 1).getToNode();
        if (!fromNode.equals(toNode)) {

            Set<String> allowedModes = new HashSet<>();
            allowedModes.addAll(link0.getAllowedModes());
            double beelineDist = CoordUtils.calcEuclideanDistance(fromNode.getCoord(), toNode.getCoord());
            String type = NetworkUtils.getType(link0);
            Integer accessControl = Integer.parseInt(String.valueOf(link0.getAttributes().getAttribute(Variables.ACCESS_CONTROLLED)));
            double length = linksToMerge.stream().mapToDouble(Link::getLength).sum();
            if (beelineDist > length) {
                length = beelineDist;
            }
            double capacity = linksToMerge.stream().mapToDouble(link -> link.getCapacity() * link.getLength()).sum() / length;
            double speed = linksToMerge.stream().mapToDouble(link -> link.getFreespeed() * link.getLength()).sum() / length;

            Id<Link> linkId = Id.createLinkId(fromNode.getId().toString() + "_m_" + toNode.getId().toString());
            if (linksToAdd.containsKey(linkId)) {
                linkId = Id.createLinkId(fromNode.getId().toString() + "_m_" + toNode.getId().toString() + "_m" + merged);
            }
            Link newLink = network.getFactory().createLink(linkId, fromNode, toNode);
            merged++;
            newLink.setFreespeed(speed);
            newLink.setCapacity(capacity);
            newLink.setAllowedModes(allowedModes);
            newLink.setNumberOfLanes(lanes);
            NetworkUtils.setType(newLink, type);
            newLink.getAttributes().putAttribute(Variables.ACCESS_CONTROLLED, accessControl);
            List<String> intermediateNodes = linksToMerge.stream().map(l -> l.getToNode().getId().toString()).collect(Collectors.toList());
            intermediateNodes.remove(toNode.getId().toString());
            newLink.getAttributes().putAttribute(VNODES, String.join(",", intermediateNodes));
            String wkt = mergeWKTPolygons(linksToMerge);
            if (wkt != null) {
                newLink.getAttributes().putAttribute(PolylinesCreator.WKT_ATTRIBUTE, wkt);
            }
            linksToAdd.put(newLink.getId(), newLink);
            linksToRemove.addAll(linksToMerge);
        }
    }

    private String mergeWKTPolygons(List<Link> linksToMerge) {
        StringBuilder wkt = new StringBuilder(128);
        wkt.append("LINESTRING(");
        boolean hasAtleastOneWKT = false;
        for (Link l : linksToMerge) {
            String s = (String) l.getAttributes().getAttribute(PolylinesCreator.WKT_ATTRIBUTE);
            if (s != null) {
                wkt.append(s, 11, s.length() - 2);
                wkt.append(" ");
                hasAtleastOneWKT = true;
            }
        }
        wkt.append(')');
        return hasAtleastOneWKT ? wkt.toString() : null;
    }

    private Link findRealOutLink(Link nextLink) {
        Link outlink = null;
        for (Link l : nextLink.getToNode().getOutLinks().values()) {
            if (l.getToNode().equals(nextLink.getFromNode())) {
            } else if (outlink == null) {
                outlink = l;
            } else {
                return null;
            }
        }
        return outlink;
    }

}
