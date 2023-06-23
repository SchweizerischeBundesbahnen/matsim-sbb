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

package ch.sbb.matsim.analysis.tripsandlegsanalysis;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.SBBModes.PTSubModes;
import ch.sbb.matsim.csv.CSVWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.core.config.groups.NetworkConfigGroup;
import org.matsim.core.network.filter.NetworkFilterManager;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scoring.ExperiencedPlansService;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import jakarta.inject.Inject;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;

import static ch.sbb.matsim.mavi.streets.VisumStreetNetworkExporter.extractVisumNodeAndLinkId;

public class PtLinkVolumeAnalyzer {

    private static final String LINK_NO = "$LINK:NO";
    private static final String FROMNODENO = "FROMNODENO";
    private static final String LINK_ID_SIM = "LINK_ID_SIM";
    private static final String VOLUME = "VOLUME";
    private static final String[] VOLUMES_COLUMNS = new String[]{LINK_NO, FROMNODENO, LINK_ID_SIM, VOLUME};
    private static final String HEADER = "$VISION\n* Schweizerische Bundesbahnen SBB Personenverkehr Bern\n* 12/09/22\n* \n* Table: Version block\n* \n$VERSION:VERSNR;FILETYPE;LANGUAGE;UNIT\n12.00;Att;ENG;KM\n\n* \n* Table: Links\n* \n";
    private final static Logger log = LogManager.getLogger(PtLinkVolumeAnalyzer.class);
    private final RailTripsAnalyzer railTripsAnalyzer;
    private final Set<Id<Link>> ptlinks;
    private final Network network;
    @Inject
    private ExperiencedPlansService experiencedPlansService;

    @Inject
    public PtLinkVolumeAnalyzer(RailTripsAnalyzer railTripsAnalyzer, TransitSchedule schedule, Network network) {
        this.railTripsAnalyzer = railTripsAnalyzer;
        this.network = network;
        this.ptlinks = network.getLinks().values()
                .stream()
                .filter(l -> l.getAllowedModes().stream().anyMatch(m -> PTSubModes.submodes.contains(m) || m.equals(SBBModes.PT)))
                .map(Identifiable::getId)
                .collect(Collectors.toSet());

    }

    public Map<Id<Link>, Long> analysePtLinkUsage() {
        Map<Id<Link>, Long> ptUsage = experiencedPlansService.getExperiencedPlans().values()
                .stream()
                .flatMap(p -> TripStructureUtils.getLegs(p).stream())
                .filter(l -> l.getMode().equals(SBBModes.PT))
                .map(l -> (TransitPassengerRoute) l.getRoute())
                .flatMap(r -> railTripsAnalyzer.getPtLinkIdsTraveledOn(r).stream())
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        return ptUsage;

    }

    public void writePtLinkUsage(String outputfile, double scalefactor) {
        NetworkFilterManager nfm = new NetworkFilterManager(network, new NetworkConfigGroup());
        nfm.addLinkFilter(l -> this.ptlinks.contains(l.getId()));
        Network ptNetwork = nfm.applyFilters();
        var ptVolumes = analysePtLinkUsage();
        try (CSVWriter writer = new CSVWriter(HEADER, VOLUMES_COLUMNS, outputfile)) {
            for (Entry<Id<Link>, Long> e : ptVolumes.entrySet()) {
                Id<Link> currentLinkId = e.getKey();
                int scaledVolume = (int) Math.round((double) e.getValue() * scalefactor);
                Link l = ptNetwork.getLinks().get(currentLinkId);
                if (l != null) {
                    l.getAttributes().putAttribute(VOLUME, scaledVolume);
                    String visumLinkSequence = (String) l.getAttributes().getAttribute("visum_link_sequence");
                    if (visumLinkSequence == null || visumLinkSequence.isEmpty()) {
                        visumLinkSequence = "-1_-1";  // parseable integers representing null
                    }
                    List<Tuple<Integer, Integer>> visumFromNodeToLinkTuples =
                            Arrays.stream(visumLinkSequence.split(","))
                                    .map(s -> extractVisumNodeAndLinkId(Id.createLinkId(s)))
                                    .filter(Objects::nonNull)
                                    .toList();
                    for (Tuple<Integer, Integer> visumFromNodeToLinkIds : visumFromNodeToLinkTuples) {
                        writer.set(LINK_ID_SIM, currentLinkId.toString());
                        writer.set(VOLUME, Integer.toString(scaledVolume));
                        writer.set(FROMNODENO, Integer.toString(visumFromNodeToLinkIds.getFirst()));
                        writer.set(LINK_NO, Integer.toString(visumFromNodeToLinkIds.getSecond()));
                        writer.writeRow();
                    }
                } else {
                    log.warn("Could not find link " + currentLinkId + ". Skipping.");
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        new NetworkWriter(ptNetwork).write(outputfile + "_network.xml.gz");
        NetworkFilterManager nfm2 = new NetworkFilterManager(ptNetwork, new NetworkConfigGroup());
        nfm2.addLinkFilter(link -> link.getAllowedModes().contains(PTSubModes.RAIL));
        Network railNet = nfm2.applyFilters();
        new NetworkWriter(railNet).write(outputfile + "_rail_network.xml.gz");
    }

}
