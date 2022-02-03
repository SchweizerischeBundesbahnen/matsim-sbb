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

package ch.sbb.matsim.analysis.linkAnalysis;

import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.mavi.streets.MergeRuralLinks;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.utils.io.UncheckedIOException;

public class CarLinkAnalysis {

    private static final String[] VOLUMES_COLUMNS = new String[]{
            "LINK_ID_SIM",
            "FROMNODENO",
            "TONODENO",
            "VOLUME_SIM"
    };
    private final Network network;
    private final double samplesize;
    final IterationLinkAnalyzer linkAnalyzer;
    private boolean firstcall = true;
    private TreeSet<Id<Link>> carlinks;

    public CarLinkAnalysis(PostProcessingConfigGroup ppConfig, Network network, IterationLinkAnalyzer linkAnalyzer) {
        this.samplesize = ppConfig.getSimulationSampleSize();
        this.network = network;
        this.linkAnalyzer = linkAnalyzer;
    }

    public void writeMultiIterationCarStats(String filename, int iteration) {
        try (OutputStream os = new GZIPOutputStream(new FileOutputStream(new File(filename), true))) {
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(os));
            if (firstcall) {
                carlinks = network.getLinks().values()
                        .stream()
                        .filter(l -> l.getAllowedModes().contains(SBBModes.CAR))
                        .map(Identifiable::getId)
                        .collect(Collectors.toCollection(TreeSet::new));
                w.write("Iteration;" + carlinks.stream().map(Objects::toString).collect(Collectors.joining(";")));
                firstcall = false;
            }
            var linkVolumes = linkAnalyzer.getIterationCounts();
            w.newLine();
            w.write(iteration);
            for (Id<Link> l : carlinks) {
                double vol = linkVolumes.getOrDefault(l, 0) / samplesize;
                w.write(";");
                w.write((int) vol);
            }

            w.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public void writeSingleIterationCarStats(String fileName) {
        var linkVolumes = linkAnalyzer.getIterationCounts();

        try (CSVWriter writer = new CSVWriter("", VOLUMES_COLUMNS, fileName)) {
            for (Map.Entry<Id<Link>, Integer> entry : linkVolumes.entrySet()) {

                Link link = network.getLinks().get(entry.getKey());
                if (link != null) {
                    if (link.getAllowedModes().contains(SBBModes.CAR)) {
                        double volume = entry.getValue();
                        String id = link.getId().toString();
                        writer.set("LINK_ID_SIM", id);
                        String vnodes = (String) link.getAttributes().getAttribute(MergeRuralLinks.VNODES);
                        final String fromNode = link.getFromNode().getId().toString().startsWith("C_") ? link.getFromNode().getId().toString().substring(2) : link.getFromNode().getId().toString();
                        final String toNode = link.getToNode().getId().toString().startsWith("C_") ? link.getToNode().getId().toString().substring(2) : link.getToNode().getId().toString();

                        if (vnodes != null) {
                            String currentFromNode = fromNode;
                            String[] nodes = vnodes.split(",");
                            for (String node : nodes) {
                                String currentToNode = node;
                                currentToNode = currentToNode.startsWith("C_") ? currentToNode.substring(2) : currentToNode;
                                writer.set("LINK_ID_SIM", id);
                                writer.set("FROMNODENO", currentFromNode);
                                writer.set("TONODENO", currentToNode);
                                writer.set("VOLUME_SIM", Integer.toString((int) (volume / samplesize)));
                                writer.writeRow();
                                currentFromNode = currentToNode;

                            }
                            writer.set("LINK_ID_SIM", id);
                            writer.set("FROMNODENO", currentFromNode);

                        } else {
                            writer.set("LINK_ID_SIM", id);
                            writer.set("FROMNODENO", fromNode);
                        }
                        writer.set("TONODENO", toNode);
                        writer.set("VOLUME_SIM", Integer.toString((int) (volume / samplesize)));
                        writer.writeRow();

                    }
                }
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}


