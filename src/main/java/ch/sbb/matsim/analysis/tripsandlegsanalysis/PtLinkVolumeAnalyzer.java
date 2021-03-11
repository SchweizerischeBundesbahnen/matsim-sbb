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
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scoring.ExperiencedPlansService;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

public class PtLinkVolumeAnalyzer {

    private final RailTripsAnalyzer railTripsAnalyzer;
    private final Set<Id<Link>> ptlinks;
    @Inject
    private ExperiencedPlansService experiencedPlansService;

    @Inject
    public PtLinkVolumeAnalyzer(RailTripsAnalyzer railTripsAnalyzer, TransitSchedule schedule, Network network) {
        this.railTripsAnalyzer = railTripsAnalyzer;
        this.ptlinks = network.getLinks().values()
                .stream()
                .filter(l -> l.getAllowedModes().stream().anyMatch(m -> PTSubModes.submodes.contains(m) || m.equals(SBBModes.PT)))
                .map(l -> l.getId())
                .collect(Collectors.toSet());

    }

    public Map<Id<Link>, Double> analysePtLinkUsage() {
        Map<Id<Link>, MutableDouble> ptUsage = ptlinks.stream().collect(Collectors.toMap((a -> a), (a -> new MutableDouble())));
        experiencedPlansService.getExperiencedPlans().values()
                .stream()
                .flatMap(p -> TripStructureUtils.getLegs(p).stream())
                .filter(l -> l.getMode().equals(SBBModes.PT))
                .map(l -> (TransitPassengerRoute) l.getRoute())
                .flatMap(r -> railTripsAnalyzer.getPtLinkIdsTraveledOn(r).stream())
                .forEach(linkId -> ptUsage.get(linkId).increment());
        return ptUsage.entrySet().stream().collect(Collectors.toMap(k -> k.getKey(), k -> k.getValue().toDouble()));

    }

    public void writePtLinkUsage(String outputfile, String runId, double scalefactor) {
        final String vol = runId + "_ptVolume";
        final String linkId = "linkId";
        String[] header = {linkId, vol};
        var ptVolumes = analysePtLinkUsage();
        try (CSVWriter writer = new CSVWriter(null, header, outputfile)) {
            for (Entry<Id<Link>, Double> e : ptVolumes.entrySet()) {
                writer.set(linkId, e.getKey().toString());
                writer.set(vol, Integer.toString((int) Math.round(e.getValue() * scalefactor)));
                writer.writeRow();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
