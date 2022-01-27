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
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class CountLinksbySL3 {

    Zones zones;

    private Network network;

    public CountLinksbySL3(String networkfile, String shapeFile) {
        this.network = NetworkUtils.createNetwork(ConfigUtils.createConfig());
        zones = ZonesLoader.loadZones("1", shapeFile, "zone_id");
        new MatsimNetworkReader(network).readFile(networkfile);


    }

    public static void main(String[] args) {
        String networkfile = args[0];
        String shapeFile = args[1];


        CountLinksbySL3 removeRuralLinks = new CountLinksbySL3(networkfile, shapeFile);
        removeRuralLinks.count();
    }


    private void count() {
        Map<String, AtomicInteger> res = new HashMap<>();
        for (Link link : network.getLinks().values()) {
            Zone zone = zones.findZone(link.getCoord());

            if (zone != null) {
                String sl3_id = zone.getAttribute("sl3_id").toString();
                res.computeIfAbsent(sl3_id, a -> new AtomicInteger()).incrementAndGet();

            } else {
                res.computeIfAbsent("none", a -> new AtomicInteger()).incrementAndGet();
            }


        }
        res.forEach((k, v) -> System.out.println(k + ": " + v));
    }


}
