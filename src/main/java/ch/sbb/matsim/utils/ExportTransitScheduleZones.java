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

package ch.sbb.matsim.utils;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import java.io.IOException;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

public class ExportTransitScheduleZones {

    public static void main(String[] args) throws IOException {
        String scheduleFile = "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_3.1.LFP\\2040\\pt\\bav_ak35\\output\\transitSchedule.xml.gz";
        String zonesFile = "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_3.1.LFP\\2040\\plans\\3.40.63\\mobi-zones.shp";
        String outputFile = "\\\\wsbbrz0283\\mobi\\99_Playgrounds\\JB\\stop_zones_mapping.csv";

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(scheduleFile);
        Zones zones = ZonesLoader.loadZones("zones", zonesFile, Variables.ZONE_ID);

        try (CSVWriter writer = new CSVWriter(null, new String[]{"no", "code", "name", "zone_id", "mun_id", "amr_id"}, outputFile)) {
            scenario.getTransitSchedule().getFacilities().values().forEach(f -> {
                var zone = zones.findZone(f.getCoord());
                if (zone != null) {
                    writer.set("zone_id", zone.getId().toString());
                    writer.set("mun_id", String.valueOf(zone.getAttribute("mun_id")));
                    writer.set("amr_id", String.valueOf(zone.getAttribute("amr_id")));
                    writer.set("name", f.getName());
                    writer.set("no", f.getId().toString());
                    writer.set("code", String.valueOf(f.getAttributes().getAttribute("03_Stop_Code")));
                    writer.writeRow();

                }
            });
        }

    }

}
