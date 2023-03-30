package ch.sbb.matsim.projects.postauto;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GenerateDRTStops {
    public static void main(String[] args) throws IOException {
        String inputSchedule = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220905_Postauto_Bucheggberg\\pt\\NPVM2020\\output\\transitSchedule.xml.gz";
        String zonesFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20221221_Postauto_OnDemand\\20230320_Wallis\\relevant_zones.txt";
        String whitelist = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220905_Postauto_Bucheggberg\\relevant_zones.txt";
        String networFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220905_Postauto_Bucheggberg\\streets\\output\\network.xml.gz";
        String outputFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220905_Postauto_Bucheggberg\\sim\\virtual-stops.xml";
        Set<String> whitelistZones;
        try (Stream<String> lines = Files.lines(Path.of(whitelist))) {
            whitelistZones = lines.collect(Collectors.toSet());
        }
        Zones zones = ZonesLoader.loadZones("zones", zonesFile, Variables.ZONE_ID);

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networFile);
        new TransitScheduleReader(scenario).readFile(inputSchedule);
        Scenario scenario2 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        TransitSchedule outputSchedule = scenario2.getTransitSchedule();
        for (TransitStopFacility stop : scenario.getTransitSchedule().getFacilities().values()) {
            Zone z = zones.findZone(stop.getCoord());
            if (z != null) {
                if (whitelistZones.contains(z.getId().toString())) {
                    Link l = NetworkUtils.getNearestLink(scenario.getNetwork(), stop.getCoord());
                    stop.setLinkId(l.getId());
                    outputSchedule.addStopFacility(stop);
                }
            }
        }
        new TransitScheduleWriter(scenario2.getTransitSchedule()).writeFile(outputFile);
    }


}
