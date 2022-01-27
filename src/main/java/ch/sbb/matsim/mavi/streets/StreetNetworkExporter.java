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
import ch.sbb.matsim.mavi.PolylinesCreator;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.algorithms.NetworkCleaner;

public class StreetNetworkExporter {

    public static void main(String[] args) throws IOException {
        final StreetsExporterConfigGroup streetsExporterConfigGroup = new StreetsExporterConfigGroup();
        Config config = ConfigUtils.loadConfig(args[0], streetsExporterConfigGroup);
        Zones zones = ZonesLoader.loadZones("1", streetsExporterConfigGroup.getZonesFile());
        new File(streetsExporterConfigGroup.getOutputDir()).mkdirs();
        VisumStreetNetworkExporter vse = new VisumStreetNetworkExporter();
        vse.run(streetsExporterConfigGroup.getVisumFile(), streetsExporterConfigGroup.getOutputDir(), Integer.parseInt(streetsExporterConfigGroup.getVisumVersion()),
                streetsExporterConfigGroup.isExportCounts(), true);
        Network network = vse.getNetwork();
        if (streetsExporterConfigGroup.getSmallRoadSpeedFactor() != 1.0 && streetsExporterConfigGroup.getMainRoadSpeedFactor() != 1.0) {
            ReduceNetworkSpeeds reduceNetworkSpeeds = new ReduceNetworkSpeeds(network, zones, streetsExporterConfigGroup.getSmallRoadSpeedFactor(),
                    streetsExporterConfigGroup.getMainRoadSpeedFactor());
            reduceNetworkSpeeds.reduceSpeeds();
        }

        if (streetsExporterConfigGroup.reduceForeignLinks) {
            Logger.getLogger(StreetNetworkExporter.class).info("Removing foreign rural links.");
            RemoveForeignRuralLinks r = new RemoveForeignRuralLinks(network, zones);
            r.removeLinks();
            NetworkCleaner cleaner = new NetworkCleaner();
            cleaner.run(network);
        }

        if (streetsExporterConfigGroup.isMergeRuralLinks()) {
            Logger.getLogger(StreetNetworkExporter.class).info("Merging rural links.");
            MergeRuralLinks l = new MergeRuralLinks(network, zones);
            l.mergeRuralLinks();
            NetworkCleaner cleaner = new NetworkCleaner();
            cleaner.run(network);
        }
        adjustRoundaboutLinks(network);
        Logger.getLogger(StreetNetworkExporter.class).info("Writing Network with polylines.");
        new NetworkWriter(network).write(streetsExporterConfigGroup.getOutputDir() + "/" + Filenames.STREET_NETWORK_WITH_POLYLINES);
        removePolylines(network);
        Logger.getLogger(StreetNetworkExporter.class).info("Writing Network without polylines.");
        new NetworkWriter(network).write(streetsExporterConfigGroup.getOutputDir() + "/" + Filenames.STREET_NETWORK);

    }

    public static void adjustRoundaboutLinks(Network network) {
        Logger.getLogger(StreetNetworkExporter.class).info("Adjusting capacities of roundabouts.");
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
        Logger.getLogger(StreetNetworkExporter.class).info("Adjusted  " + i + " link capacities in roundabouts.");
        System.out.println(adjustedLinks);
    }

    private static void removePolylines(Network network) {
        network.getLinks().values().forEach(l -> l.getAttributes().removeAttribute(PolylinesCreator.WKT_ATTRIBUTE));
    }
}
