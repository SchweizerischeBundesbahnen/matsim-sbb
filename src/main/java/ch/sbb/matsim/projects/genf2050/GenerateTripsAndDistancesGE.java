package ch.sbb.matsim.projects.genf2050;

import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.tabularFileParser.TabularFileParser;
import org.matsim.core.utils.io.tabularFileParser.TabularFileParserConfig;

import java.util.HashMap;
import java.util.Map;

import static ch.sbb.matsim.routing.access.AccessEgressModule.IS_CH;

public class GenerateTripsAndDistancesGE {


    public static void main(String[] args) {


        String folder = args[0];
        String runId = args[1];
        String prefix = folder + "/" + runId + ".";
        String networkFile = prefix + "output_network.xml.gz";
        String ptVolumes = prefix + "ptlinkvolumes.att";
        String carVolumes = prefix + "car_volumes.att";
        String zonesFile = args[2];
        Zones zones = ZonesLoader.loadZones("zones", zonesFile, "zone_id");
        final Config config = ConfigUtils.createConfig();
        config.controler().setRunId(runId);
        Scenario scenario = ScenarioUtils.createScenario(config);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);
        scenario.getNetwork().getLinks().values().forEach(l ->
                {
                    Zone zone = zones.findZone(l.getCoord());
                    boolean isInCH = zone != null && zone.getAttribute("kt_name").toString().equals("GE");
                    l.getAttributes().putAttribute(IS_CH, isInCH);
                }
        );
        Map<Id<Link>, Integer> ptvols = readPTVolumes(ptVolumes);
        Map<Id<Link>, Integer> carvols = readCarVolumes(carVolumes);
        double carPkm = scenario.getNetwork().getLinks().values().stream().filter(link -> (boolean) link.getAttributes().getAttribute(IS_CH))
                .mapToDouble(link -> (link.getLength() * carvols.getOrDefault(link.getId(), 0) / 1000.0)).sum();
        double ptPkm = scenario.getNetwork().getLinks().values().stream().filter(link -> (boolean) link.getAttributes().getAttribute(IS_CH))
                .mapToDouble(link -> (link.getLength() * ptvols.getOrDefault(link.getId(), 0) / 1000.0)).sum();
        System.out.println(runId + " pt " + ptPkm);
        System.out.println(runId + " car " + carPkm);
    }

    private static Map<Id<Link>, Integer> readPTVolumes(String file) {
        Map<Id<Link>, Integer> vols = new HashMap<>();
        TabularFileParserConfig tbc = new TabularFileParserConfig();
        tbc.setDelimiterRegex(";");
        tbc.setFileName(file);
        new TabularFileParser().parse(tbc, row -> {
            try {
                Id<Link> linkId = Id.createLinkId(row[0]);
                int vol = Integer.parseInt(row[1]);
                vols.put(linkId, vol);
            } catch (Exception e) {
            }
        });
        return vols;
    }

    private static Map<Id<Link>, Integer> readCarVolumes(String file) {
        Map<Id<Link>, Integer> vols = new HashMap<>();
        TabularFileParserConfig tbc = new TabularFileParserConfig();
        tbc.setDelimiterRegex(";");
        tbc.setFileName(file);
        new TabularFileParser().parse(tbc, row -> {
            try {
                Id<Link> linkId = Id.createLinkId(row[0]);
                int vol = Integer.parseInt(row[3]);
                vols.put(linkId, vol);
            } catch (Exception e) {
            }
        });
        return vols;
    }

}
