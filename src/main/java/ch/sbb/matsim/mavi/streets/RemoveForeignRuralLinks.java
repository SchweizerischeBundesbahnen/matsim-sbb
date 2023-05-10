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
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.network.NetworkUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class RemoveForeignRuralLinks {

    private final Set<Integer> irrelevantTypes = new HashSet<>();
    final Zones zones;

    private final Network network;

    public RemoveForeignRuralLinks(Network network, Zones zones) {

        this.network = network;
        this.zones = zones;
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

    public void removeLinks() {
        List<Link> toRemove = new ArrayList<>();
        for (Link link : network.getLinks().values()) {
            String nettype = NetworkUtils.getType(link);
            if (nettype != null) {
                int t = Integer.parseInt(nettype);
                if (irrelevantTypes.contains(t)) {
                    Zone zone = zones.findZone(link.getCoord());
                    if (zone == null) {
                        toRemove.add(link);
                    }

                }
            }
        }
        for (Link l : toRemove) {
            network.removeLink(l.getId());
        }
    }

}
