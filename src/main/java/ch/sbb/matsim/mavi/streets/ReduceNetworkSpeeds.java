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
import java.util.List;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;

public class ReduceNetworkSpeeds {

    private String ouputfile;
    private final double capslow;
    private final double capnormal;
    final Zones zones;
    final List<String> communities = List.of("261", "6621", "2701", "351", "5586", "230", "1061", "3203", "5226", "371");
    final List<String> hvs = List.of("29", "30", "31", "32");
    final List<String> nvs = List.of("50", "51", "52", "53", "54", "55", "56", "57", "58", "59", "60", "61");
    private final Network network;

    public ReduceNetworkSpeeds(String networkfile, String outputfile, String shapeFile, double capslow, double capnormal) {
        this.ouputfile = outputfile;
        this.network = NetworkUtils.createNetwork(ConfigUtils.createConfig());
        zones = ZonesLoader.loadZones("1", shapeFile, "zone_id");
        new MatsimNetworkReader(network).readFile(networkfile);
        this.capslow = capslow;
        this.capnormal = capnormal;

    }

    public ReduceNetworkSpeeds(Network network, Zones zones, double capslow, double capnormal) {

        this.network = network;
        this.zones = zones;
        this.capslow = capslow;
        this.capnormal = capnormal;

    }

    public static void main(String[] args) {
        String networkfile = args[0];
        String outputfile = args[1];
        String shapeFile = args[2];

        double capslowcitystreets = Double.parseDouble(args[3]);
        double capnormal = Double.parseDouble(args[4]);

        ReduceNetworkSpeeds reduceNetworkSpeeds = new ReduceNetworkSpeeds(networkfile, outputfile, shapeFile, capslowcitystreets, capnormal);
        reduceNetworkSpeeds.reduceSpeeds();
        reduceNetworkSpeeds.writeNetwork();
    }

    private void writeNetwork() {
        new NetworkWriter(network).write(this.ouputfile);
    }

    public void reduceSpeeds() {
        for (Link link : network.getLinks().values()) {
            String t = NetworkUtils.getType(link);
            if (hvs.contains(t)) {
                Zone zone = zones.findZone(link.getCoord());
                if (zone != null) {
                    String munid = String.valueOf(zone.getAttribute("mun_id"));
                    if (communities.contains(munid)) {
                        //                    2030:
                        link.setFreespeed(link.getFreespeed() * capnormal);
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
                            link.setFreespeed(link.getFreespeed() * capslow);
                            //                    2040:
                            //                    link.setFreespeed(link.getFreespeed()*0.6)
                        }
                    } else {

                        //                    2030:
                        link.setFreespeed(link.getFreespeed() * capnormal);
                        //                    2040:
                        //                    link.setFreespeed(link.getFreespeed()*0.8)
                    }

                }
            }
        }

    }

}
