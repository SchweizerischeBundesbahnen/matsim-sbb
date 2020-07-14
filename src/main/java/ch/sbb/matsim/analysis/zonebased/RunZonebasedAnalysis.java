package ch.sbb.matsim.analysis.zonebased;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.routing.pt.raptor.IntermodalAwareRouterModeIdentifier;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesImpl;
import ch.sbb.matsim.zones.ZonesLoader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.router.MainModeIdentifier;

/**
 * @author jbischoff / SBB
 */
public class RunZonebasedAnalysis {

    public static final String GEMEINDE = "Gemeinde";

    public static void main(String[] args) {
        String configFile = args[0];
        Config config = ConfigUtils.loadConfig(configFile, new ZoneBasedAnalysisConfigGroup());
        ZoneBasedAnalysisConfigGroup zoneBasedAnalysisConfigGroup = ConfigUtils.addOrGetModule(config, ZoneBasedAnalysisConfigGroup.class);
        List<ZonebasedAnalysisConfig> list = createAnalysisConfig(zoneBasedAnalysisConfigGroup);
        Zones zones = ZonesLoader.loadZones("analysis", zoneBasedAnalysisConfigGroup.getZonesFile(), zoneBasedAnalysisConfigGroup.getZonesIdAttribute());
        RunZonebasedAnalysis zoneBasedAnalysis = new RunZonebasedAnalysis();
        Map<String, Map<Id<Zone>, ZoneBasedAnalysis.ZoneStats>> stats = zoneBasedAnalysis.run(zones, list);
        zoneBasedAnalysis.writeCSV((ZonesImpl) zones, zoneBasedAnalysisConfigGroup.getReportFile(), stats);
    }

    private static List<ZonebasedAnalysisConfig> createAnalysisConfig(ZoneBasedAnalysisConfigGroup configGroup) {
        List<ZonebasedAnalysisConfig> runs = new ArrayList<>();
        Set<String> runIds = new HashSet<>();
        for (ZoneBasedAnalysisConfigGroup.AnalysisRunParameterSet parameterSet : configGroup.getRuns()) {
            ZonebasedAnalysisConfig config = new ZonebasedAnalysisConfig();
            config.runId = parameterSet.getRunId();
            if (config.runId == null) {
                config.runId = "run_" + (runIds.size() + 1);
            }
            if (runIds.contains(config.runId)) {
                config.runId = config.runId + "_" + (runIds.size() + 1);
            }
            runIds.add(config.runId);
            String prefix = "/output_";
            if (parameterSet.getRunId() != null) {
                prefix = "/" + parameterSet.getRunId() + ".output_";
            }
            String folderPath = parameterSet.getRunFolder() + prefix;
            config.facilitiesFile = folderPath + Controler.DefaultFiles.facilities + ".xml.gz";
            config.plansFile = folderPath + "experienced_plans.xml.gz";
            config.networkFile = folderPath + Controler.DefaultFiles.network + ".xml.gz";
            config.transitScheduleFile = folderPath + Controler.DefaultFiles.transitSchedule + ".xml.gz";
            config.configFile = folderPath + Controler.DefaultFiles.config + ".xml";
            runs.add(config);
        }
        return runs;

    }

    public Map<String, Map<Id<Zone>, ZoneBasedAnalysis.ZoneStats>> run(Zones zones, List<ZonebasedAnalysisConfig> analysisConfigs) {
        Map<String, Map<Id<Zone>, ZoneBasedAnalysis.ZoneStats>> stats = new HashMap<>();
        for (ZonebasedAnalysisConfig config : analysisConfigs) {
            MainModeIdentifier identifier = new IntermodalAwareRouterModeIdentifier(RunSBB.buildConfig(config.configFile));
            ZoneBasedAnalysis zoneBasedAnalysis = new ZoneBasedAnalysis(zones, config, identifier);
            stats.put(config.runId, zoneBasedAnalysis.runAnalysis());

        }
        return stats;
    }

    public void writeCSV(ZonesImpl zones, String fileName, Map<String, Map<Id<Zone>, ZoneBasedAnalysis.ZoneStats>> stats) {
        Set<String> ptSubmodes = stats.values()
                .stream()
                .flatMap(m -> m.values().stream())
                .flatMap(zoneStats -> zoneStats.modalPtDepartures.keySet().stream())
                .collect(Collectors.toSet());
        Set<String> travelModes = stats.values()
                .stream()
                .flatMap(m -> m.values().stream())
                .flatMap(zoneStats -> zoneStats.travelDistance.keySet().stream())
                .collect(Collectors.toSet());
        List<String> header = buildHeader(ptSubmodes, travelModes, stats.keySet());
        try (CSVWriter writer = new CSVWriter(null, header.toArray(new String[header.size()]), fileName)) {
            for (Zone zone : zones.getZones()) {
                writer.set(ZoneBasedAnalysis.ZONE_ID, zone.getId().toString());
                writer.set(GEMEINDE, String.valueOf(zone.getAttribute("N_Gem")));
                for (Map.Entry<String, Map<Id<Zone>, ZoneBasedAnalysis.ZoneStats>> zonalRun : stats.entrySet()) {
                    String run = zonalRun.getKey();
                    ZoneBasedAnalysis.ZoneStats zoneStats = zonalRun.getValue().get(zone.getId());
                    for (Map.Entry<String, Integer> dep : zoneStats.modalPtDepartures.entrySet()) {
                        String column = run + "_" + dep.getKey() + ZoneBasedAnalysis.ZoneStats.DEPARTURES;
                        writer.set(column, Integer.toString(dep.getValue()));
                    }
                    for (Map.Entry<String, MutableInt> dep : zoneStats.ptdeBoardings.entrySet()) {
                        String column = run + "_" + dep.getKey() + ZoneBasedAnalysis.ZoneStats.DEBOARDINGS;
                        writer.set(column, dep.getValue().toString());
                    }
                    for (Map.Entry<String, MutableInt> dep : zoneStats.ptBoardings.entrySet()) {
                        String column = run + "_" + dep.getKey() + ZoneBasedAnalysis.ZoneStats.BOARDINGS;
                        writer.set(column, dep.getValue().toString());
                    }
                    for (Map.Entry<String, DescriptiveStatistics> tt : zoneStats.travelTimes.entrySet()) {
                        String column = run + "_" + tt.getKey() + ZoneBasedAnalysis.ZoneStats.TRIPS;
                        writer.set(column, Long.toString(tt.getValue().getN()));
                        String column2 = run + "_" + tt.getKey() + ZoneBasedAnalysis.ZoneStats.AVERAGE_TRAVEL_TIME;
                        writer.set(column2, Double.toString(tt.getValue().getMean()));
                    }
                    for (Map.Entry<String, DescriptiveStatistics> tt : zoneStats.travelDistance.entrySet()) {
                        String column = run + "_" + tt.getKey() + ZoneBasedAnalysis.ZoneStats.AVERAGE_TRAVEL_DISTANCE;
                        writer.set(column, Double.toString(tt.getValue().getMean()));
                    }
                    String column = run + ZoneBasedAnalysis.ZoneStats.TRANSFERS;
                    writer.set(column, Double.toString(zoneStats.transfers.getMean()));

                }
                writer.writeRow();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<String> buildHeader(Set<String> ptSubmodes, Set<String> travelModes, Set<String> runs) {
        List<String> header = new ArrayList<>();
        header.add(ZoneBasedAnalysis.ZONE_ID);
        header.add(GEMEINDE);
        for (String run : runs) {
            for (String sm : ptSubmodes) {
                header.add(run + "_" + sm + ZoneBasedAnalysis.ZoneStats.DEPARTURES);
                header.add(run + "_" + sm + ZoneBasedAnalysis.ZoneStats.BOARDINGS);
                header.add(run + "_" + sm + ZoneBasedAnalysis.ZoneStats.DEBOARDINGS);
            }

            for (String sm : travelModes) {
                header.add(run + "_" + sm + ZoneBasedAnalysis.ZoneStats.TRIPS);
                header.add(run + "_" + sm + ZoneBasedAnalysis.ZoneStats.AVERAGE_TRAVEL_TIME);
                header.add(run + "_" + sm + ZoneBasedAnalysis.ZoneStats.AVERAGE_TRAVEL_DISTANCE);
                if (sm.equals(TransportMode.pt)) {
                    header.add(run + ZoneBasedAnalysis.ZoneStats.TRANSFERS);
                }
            }

        }
        return header;
    }

    static class ZonebasedAnalysisConfig {
        String runId;
        String plansFile;
        String transitScheduleFile;
        String facilitiesFile;
        String networkFile;
        String configFile;
    }
}

