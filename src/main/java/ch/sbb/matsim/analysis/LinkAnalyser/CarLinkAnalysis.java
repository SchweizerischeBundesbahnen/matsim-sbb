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

package ch.sbb.matsim.analysis.LinkAnalyser;

import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.csv.CSVWriter;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
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
    IterationLinkAnalyzer linkAnalyzer;
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
                        .map(l -> l.getId())
                        .collect(Collectors.toCollection(TreeSet::new));
                w.write("Iteration;" + carlinks.stream().map(Objects::toString).collect(Collectors.joining(";")));
                firstcall = false;
            }
            var linkVolumes = linkAnalyzer.getIterationCounts();
            String iterationLine = Integer.toString(iteration);
            w.newLine();
            for (Id<Link> l : carlinks) {
                double vol = linkVolumes.getOrDefault(l, 0) / samplesize;
                iterationLine = iterationLine + ";" + (int) vol;
            }
            w.write(iterationLine);
            w.flush();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
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
                        final String fromNode = link.getFromNode().getId().toString().startsWith("C_") ? link.getFromNode().getId().toString().substring(2) : link.getFromNode().getId().toString();
                        writer.set("FROMNODENO", fromNode);
                        final String toNode = link.getToNode().getId().toString().startsWith("C_") ? link.getToNode().getId().toString().substring(2) : link.getToNode().getId().toString();
                        writer.set("TONODENO", toNode);
                        writer.set("VOLUME_SIM", Double.toString(volume / samplesize));
                        writer.writeRow();
                    }
                }
            }

        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}


