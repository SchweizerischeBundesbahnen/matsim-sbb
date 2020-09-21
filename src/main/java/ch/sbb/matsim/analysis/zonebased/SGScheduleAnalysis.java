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

package ch.sbb.matsim.analysis.zonebased;

import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.ZonesImpl;
import ch.sbb.matsim.zones.ZonesLoader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang3.mutable.MutableInt;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

public class SGScheduleAnalysis {

    public static void main(String[] args) throws IOException {
        ZonesImpl zones = (ZonesImpl) ZonesLoader.loadZones("analysis", args[0], "ID");
        Scenario baseScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(baseScenario).readFile(args[1]);
        ZoneBasedAnalysis.prepareSchedule(zones, baseScenario.getTransitSchedule());
        List<TransitSchedule> compareSchedules = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
            Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
            new TransitScheduleReader(scenario).readFile(args[i]);
            ZoneBasedAnalysis.prepareSchedule(zones, scenario.getTransitSchedule());
            compareSchedules.add(scenario.getTransitSchedule());
        }
        Map<Id<Zone>, Map<Id<TransitLine>, MutableInt>> referenceDepartures = new HashMap<>();
        zones.getZones().forEach(zone -> referenceDepartures.put(zone.getId(), new HashMap<>()));
        Set<Id<TransitLine>> allLines = new HashSet<>();
        for (TransitLine line : baseScenario.getTransitSchedule().getTransitLines().values()) {
            allLines.add(line.getId());
            for (TransitRoute route : line.getRoutes().values()) {
                int departures = route.getDepartures().size();
                for (var stop : route.getStops()) {
                    Id<Zone> zone = (Id<Zone>) stop.getStopFacility().getAttributes().getAttribute(ZoneBasedAnalysis.ZONE_ID);
                    if (zone != null) {
                        referenceDepartures.get(zone).computeIfAbsent(line.getId(), (a) -> new MutableInt()).add(departures);
                    }
                }
            }
        }
        Map<Id<Zone>, Map<Id<TransitLine>, List<MutableInt>>> compareDepartures = new HashMap<>();
        zones.getZones().forEach(zone -> compareDepartures.put(zone.getId(), new HashMap<>()));
        int i = 0;
        for (var schedule : compareSchedules) {
            for (TransitLine line : schedule.getTransitLines().values()) {
                allLines.add(line.getId());
                for (TransitRoute route : line.getRoutes().values()) {
                    int departures = route.getDepartures().size();
                    for (var stop : route.getStops()) {
                        Id<Zone> zone = (Id<Zone>) stop.getStopFacility().getAttributes().getAttribute(ZoneBasedAnalysis.ZONE_ID);
                        if (zone != null) {
                            compareDepartures.get(Id.create(zone, Zone.class))
                                    .computeIfAbsent(line.getId(), (a) -> List.of(new MutableInt(), new MutableInt(), new MutableInt()))
                                    .get(i).add(departures);
                        }
                    }
                }
            }
            i++;
        }
        List<String> result = new ArrayList<>();
        for (var lid : allLines) {
            for (Zone zone : zones.getZones()) {
                var zoneId = zone.getId();
                int base = referenceDepartures.get(zoneId).getOrDefault(lid, new MutableInt()).intValue();
                int[] comp = new int[compareSchedules.size()];
                var cids = compareDepartures.get(zoneId).get(lid);
                boolean hasDiffs = false;
                if (cids != null) {
                    for (int j = 0; j < compareSchedules.size(); j++) {
                        comp[j] = cids.get(j).intValue();
                        if (!hasDiffs) {
                            hasDiffs = base != comp[j];
                        }
                    }
                }
                if (hasDiffs) {
                    String diffs = "";
                    for (int j = 0; j < comp.length; j++) {
                        diffs = diffs + ";" + comp[j];
                    }
                    String com = (String) zone.getAttribute("N_Gem");
                    result.add(lid.toString() + ";" + com + ";" + zoneId.toString() + ";" + base + diffs);
                }

            }
        }
        BufferedWriter bw = IOUtils.getBufferedWriter("scheduleanalysis.csv");
        bw.write("Line;Zone;Com;Reference;");
        for (var l : result) {
            bw.newLine();
            bw.write(l);

        }

        bw.flush();
        bw.close();

    }

}
