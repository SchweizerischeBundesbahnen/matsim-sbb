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

package ch.sbb.matsim.preparation.casestudies;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.TravelTimeCalculatorConfigGroup;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkChangeEvent;
import org.matsim.core.network.NetworkChangeEvent.ChangeType;
import org.matsim.core.network.NetworkChangeEvent.ChangeValue;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkChangeEventsWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;

public class GenerateNetworkChangeEvents {

    private final int ENDTIME = 32 * 3600;
    private final int TIMESTEP = 15 * 60;
    private final String NETWORKFILE = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20210623_Agy\\streets\\output\\network.xml.gz";
    private final String EVENTSFILE = "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_3.1.LFP\\2040\\sim\\3.40.53\\output_slice0\\MOBI34053.output_events.xml.gz";
    private final String CHANGEFILE = "C:\\devsbb\\changeEvents.xml.gz";
    private final String BLACKLISTZONES = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20210623_Agy\\sim\\reduce\\fribourgzones.txt";
    private final String MOBIZONES = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20210623_Agy\\plans\\mobi-zones.shp";
    private final double MINIMUMFREESPEED = 3;
    private Scenario sc;
    private TravelTimeCalculator tcc;
    private List<NetworkChangeEvent> networkChangeEvents;
    private Set<Id<Link>> blacklistlinks;

    public GenerateNetworkChangeEvents() {
        this.networkChangeEvents = new ArrayList<>();

    }

    public static void main(String[] args) {
        GenerateNetworkChangeEvents ncg = new GenerateNetworkChangeEvents();
        ncg.run();

    }

    private void run() {
        prepareScen();
        tcc = readEvents();
        createNetworkChangeEvents(sc.getNetwork(), tcc);
        new NetworkChangeEventsWriter().write(CHANGEFILE, networkChangeEvents);
    }

    public void createNetworkChangeEvents(Network network, TravelTimeCalculator tcc2) {
        for (Link l : network.getLinks().values()) {
            if ((l.getAllowedModes().size() == 1) && l.getAllowedModes().contains("pt")) {
                continue;
            }
            if (blacklistlinks.contains(l.getId())) {
                continue;
            }
            double length = l.getLength();
            double previousTravelTime = l.getLength() / l.getFreespeed();

            for (double time = 0; time < ENDTIME; time = time + TIMESTEP) {

                double newTravelTime = tcc2.getLinkTravelTimes().getLinkTravelTime(l, time, null, null);
                if (newTravelTime != previousTravelTime) {
                    NetworkChangeEvent nce = new NetworkChangeEvent(time);
                    nce.addLink(l);
                    double newFreespeed = length / newTravelTime;
                    if (newFreespeed < MINIMUMFREESPEED) {
                        newFreespeed = MINIMUMFREESPEED;
                    }
                    ChangeValue freespeedChange = new ChangeValue(ChangeType.ABSOLUTE_IN_SI_UNITS, newFreespeed);
                    nce.setFreespeedChange(freespeedChange);
                    this.networkChangeEvents.add(nce);
                    previousTravelTime = newTravelTime;
                }
            }
        }
    }

    private void prepareScen() {

        try {
            sc = ScenarioUtils.createScenario(ConfigUtils.createConfig());
            new MatsimNetworkReader(sc.getNetwork()).readFile(NETWORKFILE);
            Zones zones = ZonesLoader.loadZones("zones", MOBIZONES, Variables.ZONE_ID);
            Set<Id<Zone>> whitelistZones = Files.lines(Path.of(BLACKLISTZONES)).map(s -> Id.create(s, Zone.class)).collect(Collectors.toSet());
            this.blacklistlinks = sc.getNetwork().getLinks().values().parallelStream().filter(l -> {
                var z = zones.findZone(l.getFromNode().getCoord());
                if (z != null) {
                    if (whitelistZones.contains(z.getId())) {
                        return true;
                    }
                }
                return false;
            }).map(Link::getId).collect(Collectors.toSet());

        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    private TravelTimeCalculator readEvents() {
        EventsManager manager = EventsUtils.createEventsManager();

        TravelTimeCalculatorConfigGroup ttccg = new TravelTimeCalculatorConfigGroup();
        TravelTimeCalculator tc = new TravelTimeCalculator(sc.getNetwork(), ttccg);
        manager.addHandler(tc);
        new MatsimEventsReader(manager).readFile(EVENTSFILE);
        return tc;
    }

    public List<NetworkChangeEvent> getNetworkChangeEvents() {
        return networkChangeEvents;
    }

}
