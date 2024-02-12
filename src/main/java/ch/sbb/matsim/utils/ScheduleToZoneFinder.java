package ch.sbb.matsim.utils;

import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ScheduleToZoneFinder {


    public static void main(String[] args) {
        List<String> relevantAttributes = List.of("zone_id",
                "agglo_id", "agglo_name", "amgr_id", "amgr_name", "amr_id",
                "amr_name", "kt_id", "kt_name", "msr_id", "msr_name",
                "mun_id", "mun_name"
        );
        String transitScheduleFile = "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_4.0\\2050\\pt\\BAVAK35_mit_oev_Taktverdichtung_feederExtended\\output\\transitSchedule.xml.gz";
        String zonesFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20230825_Grenzguertel\\plans\\v7\\mobi-zones.shp";
        String outputFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\Flughafenverkehr\\stops2zones.csv";
        Zones zones = ZonesLoader.loadZones("zones", zonesFile);
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(transitScheduleFile);
        List<String> header = new ArrayList<>();
        header.add("stop_id");
        header.add("stop_name");
        header.addAll(relevantAttributes);
        try (CSVWriter writer = new CSVWriter(null, header.toArray(new String[header.size()]), outputFile)) {
            for (var stop : scenario.getTransitSchedule().getFacilities().values()) {
                Zone zone = zones.findZone(stop.getCoord());
                if (zone != null) {
                    writer.set("stop_id", stop.getId().toString());
                    writer.set("stop_name", stop.getName());
                    for (String att : relevantAttributes) {
                        writer.set(att, String.valueOf(zone.getAttribute(att)));
                    }
                    writer.writeRow();
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

}
