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

package ch.sbb.matsim.preparation;

import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import java.util.List;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;

public class ReduceNetworkSpeeds {

    private final String ouputfile;
    Zones zones;
    List<String> communities = List.of("261", "6621", "2701", "351", "5586", "230", "1061", "3203", "5226", "371");
    List<String> hvs = List.of("29", "30", "31", "32");
    List<String> nvs = List.of("50", "51", "52", "53", "54", "55", "56", "57", "58", "59", "60", "61");
    private Network network;

    public ReduceNetworkSpeeds(String networkfile, String outputfile, String shapeFile) {
        this.ouputfile = outputfile;
        this.network = NetworkUtils.createNetwork();
        zones = ZonesLoader.loadZones("1", shapeFile, "zone_id");
        new MatsimNetworkReader(network).readFile(networkfile);

    }

    public static void main(String[] args) {
        String networkfile = args[0];
        String outputfile = args[1];

        String shapeFile = args[2];
        ReduceNetworkSpeeds reduceNetworkSpeeds = new ReduceNetworkSpeeds(networkfile, outputfile, shapeFile);
        reduceNetworkSpeeds.reduce();
        reduceNetworkSpeeds.writeNetwork();
    }

    private void writeNetwork() {
        new NetworkWriter(network).write(this.ouputfile);
    }

    private void reduce() {
        for (Link link : network.getLinks().values()) {
            String t = NetworkUtils.getType(link);
            if (hvs.contains(t)) {
                Zone zone = zones.findZone(link.getCoord());
                if (zone != null) {
                    String munid = String.valueOf(zone.getAttribute("mun_id"));
                    if (communities.contains(munid)) {
                        //                    2030:
                        link.setFreespeed(link.getFreespeed() * 0.9);
                        //                    2040:
                        //                    link.setFreespeed(link.getFreespeed()*0.8);
                    }

                }
            } else if (nvs.contains(t)) {
                Zone zone = zones.findZone(link.getCoord());
                if (zone != null) {
                    String munid = String.valueOf(zone.getAttribute("mun_id"));
                    if (communities.contains(munid)) {
                        var fs = link.getFreespeed();
                        if (fs > 25 / 3.6) {

                            //                    2030:
                            link.setFreespeed(link.getFreespeed() * 0.8);
                            //                    2040:
                            //                    link.setFreespeed(link.getFreespeed()*0.6)
                        }
                    } else {

                        //                    2030:
                        link.setFreespeed(link.getFreespeed() * 0.9);
                        //                    2040:
                        //                    link.setFreespeed(link.getFreespeed()*0.8)
                    }

                }
            }
        }

    }

}
