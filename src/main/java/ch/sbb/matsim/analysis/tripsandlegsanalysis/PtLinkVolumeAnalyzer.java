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
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.core.config.groups.NetworkConfigGroup;
import org.matsim.core.network.filter.NetworkFilterManager;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scoring.ExperiencedPlansService;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import javax.inject.Inject;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PtLinkVolumeAnalyzer {

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

    public void writePtLinkUsage(String outputfile, String runId, double scalefactor) {
        NetworkFilterManager nfm = new NetworkFilterManager(network, new NetworkConfigGroup());
        nfm.addLinkFilter(l -> this.ptlinks.contains(l.getId()));
        Network ptNetwork = nfm.applyFilters();
        final String vol = runId + "_ptVolume";
        final String linkId = "linkId";
        String[] header = {linkId, vol};
        var ptVolumes = analysePtLinkUsage();
        try (CSVWriter writer = new CSVWriter(null, header, outputfile + ".csv")) {
            for (Entry<Id<Link>, Long> e : ptVolumes.entrySet()) {
                Id<Link> currentLinkId = e.getKey();
                writer.set(linkId, currentLinkId.toString());
                int scaledVolume = (int) Math.round((double) e.getValue() * scalefactor);
                writer.set(vol, Integer.toString(scaledVolume));
                writer.writeRow();
                Link l = ptNetwork.getLinks().get(currentLinkId);
                if (l != null) {
                    l.getAttributes().putAttribute(vol, scaledVolume);
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
