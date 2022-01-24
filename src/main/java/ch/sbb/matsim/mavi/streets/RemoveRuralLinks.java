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

import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RemoveRuralLinks {

    private final String ouputfile;
    private final Set<Integer> irrelevantTypes = new HashSet<>();
    Zones zones;

    private Network network;

    public RemoveRuralLinks(String networkfile, String outputfile, String shapeFile) {
        this.ouputfile = outputfile;
        this.network = NetworkUtils.createNetwork(ConfigUtils.createConfig());
        zones = ZonesLoader.loadZones("1", shapeFile, "zone_id");
        new MatsimNetworkReader(network).readFile(networkfile);
        for (int i = 38; i < 62; i++) {
            irrelevantTypes.add(i);
        }
        for (int i = 87; i < 96; i++) {
            irrelevantTypes.add(i);
        }
        irrelevantTypes.remove(41);
        irrelevantTypes.remove(42);
        irrelevantTypes.remove(53);
        irrelevantTypes.remove(54);

    }

    public static void main(String[] args) {
        String networkfile = args[0];
        String outputfile = args[1];
        String shapeFile = args[2];

        RemoveRuralLinks removeRuralLinks = new RemoveRuralLinks(networkfile, outputfile, shapeFile);
        removeRuralLinks.reduce();
        removeRuralLinks.writeNetwork();
    }

    private void writeNetwork() {
        org.matsim.core.network.algorithms.NetworkCleaner cleaner = new org.matsim.core.network.algorithms.NetworkCleaner();
        cleaner.run(network);
        new NetworkWriter(network).write(this.ouputfile);
    }

    private void reduce() {
        List<Link> toRemove = new ArrayList<>();
        for (Link link : network.getLinks().values()) {
            int t = Integer.parseInt(NetworkUtils.getType(link));
            if (irrelevantTypes.contains(t)) {
                Zone zone = zones.findZone(link.getCoord());
                if (zone == null) {
                    toRemove.add(link);
                }

            }
        }
        for (Link l : toRemove) {
            network.removeLink(l.getId());
        }
    }

}
