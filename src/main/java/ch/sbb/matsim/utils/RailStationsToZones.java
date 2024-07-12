package ch.sbb.matsim.utils;


import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class RailStationsToZones {

    public static void main(String[] args) throws IOException {

        Zones zones = ZonesLoader.loadZones("zones", "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans\\8_synpop_v4\\output\\8_synpop_v4.mobi-zones.shp");
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile("\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\pt\\NPVM2023\\output\\transitSchedule.xml.gz");
        List<ZoneRecord> zoneRecordList = new ArrayList<>();
        try (CSVReader reader = new CSVReader("\\\\filer16l\\p-v160l\\SIMBA.A11244\\40_Projekte\\20220826_Prognose_VP2050\\20_Arbeiten\\24_AltSzen_Basis23\\30_Grundwachstum_regionalisiert\\01_Liste_Bezirke_HS\\bezirke-stops.csv", ";")) {
            var line = reader.readLine();
            while (line != null) {
                Integer areaNo = Integer.parseInt(line.get("Bezirk-Nummer"));
                String areaCode = line.get("Bezirk-Code");
                String areaName = line.get("Bezirk-Name");
                var stopIds = CollectionUtils.stringToSet(line.get("Haltestelle-Nummern")).stream().map(s -> Id.create(s, TransitStopFacility.class)).collect(Collectors.toSet());
                ZoneRecord record = new ZoneRecord(areaNo, areaCode, areaName, stopIds);
                zoneRecordList.add(record);
                line = reader.readLine();
            }
        }
        Map<String, String> zoneToZoneMap = new HashMap<>();
        for (var record : zoneRecordList) {
            String npvmZone = "";
            for (var stopId : record.stopIds) {
                var stop = scenario.getTransitSchedule().getFacilities().get(stopId);
                if (stop != null) {
                    Coord coord = stop.getCoord();
                    Zone zone = zones.findZone(coord);
                    if (zone != null) {
                        npvmZone = zone.getId().toString();
                        break;
                    }
                }
            }
            zoneToZoneMap.put(String.valueOf(record.areaNo), npvmZone);
        }

        String bezno = "Bezirk-Nummer";
        String npvmzone = "NPVM-Zone";
        try (CSVWriter writer = new CSVWriter(List.of(bezno, npvmzone), "\\\\filer16l\\p-v160l\\SIMBA.A11244\\40_Projekte\\20220826_Prognose_VP2050\\20_Arbeiten\\24_AltSzen_Basis23\\30_Grundwachstum_regionalisiert\\01_Liste_Bezirke_HS\\bezirke-npvm-zonen.csv")) {
            for (var e : zoneToZoneMap.entrySet()) {
                writer.set(bezno, e.getKey());
                writer.set(npvmzone, e.getValue());
                writer.writeRow();
            }
        }

    }

    private record ZoneRecord(Integer areaNo, String stopCode, String stopName, Set<Id<TransitStopFacility>> stopIds) {
    }
}
