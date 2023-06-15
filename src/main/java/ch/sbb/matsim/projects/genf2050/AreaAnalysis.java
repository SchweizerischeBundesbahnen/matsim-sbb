package ch.sbb.matsim.projects.genf2050;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.tabularFileParser.TabularFileParser;
import org.matsim.core.utils.io.tabularFileParser.TabularFileParserConfig;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;


public class AreaAnalysis {
    public static final String IS_IN = "isIn";
    private final Map<String, String> runs;
    private final Network roadNetwork;
    private final Zones zones;
    Map<String, RunVolumeData> runVolumeDataMap = new TreeMap<>();


    public AreaAnalysis(Network roadNetwork, Zones zones, Map<String, String> runs) {
        this.roadNetwork = roadNetwork;
        this.zones = zones;
        this.runs = runs;
        assignZone(roadNetwork);

    }

    public static void main(String[] args) {
        Map<String, String> runs = new TreeMap<>();
        String root = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220411_Genf_2050\\sim\\";
        runs.put("10.0", root + "10.0-sans_I_avec_Furet");
        runs.put("10.5", root + "10.5-sans_I_avec_Furet_miv");
        runs.put("5.0", root + "5.0-ref_ak_35");
        runs.put("5.5", root + "5.5-ref_ak_35_miv");
        runs.put("6.0", root + "6.0-netzplan-sma");
        runs.put("6.5", root + "6.5-netzplan-sma_miv");
        runs.put("7.0", root + "7.0-metroX");
        runs.put("7.5", root + "7.5-metroX_miv");
        runs.put("8.0", root + "8.0-max");
        runs.put("8.5", root + "8.5-max_miv");
        runs.put("9.0", root + "9.0-x4_champel");
        runs.put("9.5", root + "9.5-x4_champel_miv");

        String roadNet = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220411_Genf_2050\\streets\\ref\\output\\network.xml.gz";
        String zonesFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220411_Genf_2050\\Auswertungen\\zonen\\kanton_ge.shp";
        String outputFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220411_Genf_2050\\sim\\Auswertungen\\Modalsplit\\perimeter-ge.csv";
        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(roadNet);
        Zones zones = ZonesLoader.loadZones(Variables.ZONE_ID, zonesFile);
        AreaAnalysis areaAnalysis = new AreaAnalysis(network, zones, runs);
        areaAnalysis.run();
        areaAnalysis.write(outputFile);
    }

    private static double getAKM(Map<Id<Link>, Map<String, Integer>> departuresPerLink, List<Link> relevantPtLinks, String mode) {

        return relevantPtLinks.stream().filter(link -> link.getAllowedModes().contains(mode)).mapToDouble(link -> departuresPerLink.get(link.getId()).getOrDefault(mode, 0) * link.getLength() * (double) link.getAttributes().getAttribute(IS_IN) * 0.001).sum();
    }

    private static double getPKM(String attribute, Link link) {
        Integer volume = (Integer) link.getAttributes().getAttribute(attribute);
        double partIn = (double) link.getAttributes().getAttribute(IS_IN);
        return volume == null ? 0 : volume * link.getLength() * 0.001 * partIn;

    }

    private static Map<Id<Link>, Double> readCarVolumes(String file) {
        Map<Id<Link>, Double> vols = new HashMap<>();
        TabularFileParserConfig tbc = new TabularFileParserConfig();
        tbc.setDelimiterRegex(";");
        tbc.setFileName(file);
        new TabularFileParser().parse(tbc, row -> {
            try {
                Id<Link> linkId = Id.createLinkId(row[3]);
                double vol = Integer.parseInt(row[4]) + Integer.parseInt(row[5]);
                vols.put(linkId, vol);
            } catch (Exception e) {
            }
        });
        return vols;
    }

    private void assignZone(Network network) {
        for (Link l : network.getLinks().values()) {
            Zone fromZone = this.zones.findZone(l.getFromNode().getCoord());
            Zone toZone = this.zones.findZone(l.getToNode().getCoord());
            double partIn = 0.0;
            if (fromZone != null || toZone != null) {
                partIn = 1.0;
            } else if (fromZone != null) {
                double startX = l.getFromNode().getCoord().getX();
                double startY = l.getFromNode().getCoord().getY();
                double xvector = l.getToNode().getCoord().getX() - startX;
                double yvector = l.getToNode().getCoord().getY() - startY;
                double i = 0.0;
                for (; i < 1.0; i = i + 0.05) {
                    Coord newCoord = new Coord(startX + i * xvector, startY + i * yvector);
                    Zone newToZone = this.zones.findZone(newCoord);
                    if (newToZone == null) {
                        break;
                    }
                }
                partIn = i;
            } else if (toZone != null) {
                double startX = l.getToNode().getCoord().getX();
                double startY = l.getToNode().getCoord().getY();
                double xvector = startX - l.getFromNode().getCoord().getX();
                double yvector = startY - l.getFromNode().getCoord().getY();
                double i = 0.0;
                for (; i < 1.0; i = i + 0.05) {
                    Coord newCoord = new Coord(startX + i * xvector, startY + i * yvector);
                    Zone newToZone = this.zones.findZone(newCoord);
                    if (newToZone == null) {
                        break;
                    }
                }
                partIn = i;
            }


            l.getAttributes().putAttribute(IS_IN, partIn);
        }
    }

    private void write(String outputFile) {
        String[] header = new String[runVolumeDataMap.size() + 1];
        header[0] = "means";
        int i = 1;
        for (String s : runVolumeDataMap.keySet()) {
            header[i] = s;
            i++;
        }

        try (CSVWriter writer = new CSVWriter(null, header, outputFile)) {
            writer.set("means", SBBModes.CAR);
            for (RunVolumeData d : runVolumeDataMap.values()) {
                writer.set(d.run, Double.toString(d.carVolume()));
            }
            writer.writeRow();
            writer.set("means", SBBModes.PTSubModes.RAIL);
            for (RunVolumeData d : runVolumeDataMap.values()) {
                writer.set(d.run, Double.toString(d.trainVolume()));
            }
            writer.writeRow();
            writer.set("means", SBBModes.PTSubModes.TRAM);
            for (RunVolumeData d : runVolumeDataMap.values()) {
                writer.set(d.run, Double.toString(d.tramVolume()));
            }
            writer.writeRow();
            writer.set("means", SBBModes.PTSubModes.BUS);
            for (RunVolumeData d : runVolumeDataMap.values()) {
                writer.set(d.run, Double.toString(d.busVolume()));
            }
            writer.writeRow();
            writer.set("means", SBBModes.PTSubModes.OTHER);
            for (RunVolumeData d : runVolumeDataMap.values()) {
                writer.set(d.run, Double.toString(d.otherVolume()));
            }

            writer.writeRow();
            writer.writeRow();
            writer.set("means", "AKM");
            writer.writeRow();
            writer.set("means", SBBModes.PTSubModes.RAIL);
            for (RunVolumeData d : runVolumeDataMap.values()) {
                writer.set(d.run, Double.toString(d.railAKM()));
            }
            writer.writeRow();
            writer.set("means", SBBModes.PTSubModes.TRAM);
            for (RunVolumeData d : runVolumeDataMap.values()) {
                writer.set(d.run, Double.toString(d.tramAKM()));
            }
            writer.writeRow();
            writer.set("means", SBBModes.PTSubModes.BUS);
            for (RunVolumeData d : runVolumeDataMap.values()) {
                writer.set(d.run, Double.toString(d.busAKM()));
            }
            writer.writeRow();
            writer.set("means", SBBModes.PTSubModes.OTHER);
            for (RunVolumeData d : runVolumeDataMap.values()) {
                writer.set(d.run, Double.toString(d.otherAKM()));
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void run() {
        List<Link> relevantCarLinks = roadNetwork.getLinks().values().stream().filter(link -> (double) link.getAttributes().getAttribute(IS_IN) > 0.0).collect(Collectors.toList());
        for (var run : runs.entrySet()) {
            String runId = run.getKey();
            String path = run.getValue();
            String ptNet = path + "/output/" + runId + ".ptlinkvolumes_network.xml.gz";
            String scheduleFile = path + "/output/" + runId + ".output_transitSchedule.xml.gz";
            String carVolumesFile = path + "/output/" + runId + ".car_volumes.att";
            Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
            new TransitScheduleReader(scenario).readFile(scheduleFile);
            var departuresPerLink = calcDeparturesPerLink(scenario.getTransitSchedule());
            Network ptNetwork = scenario.getNetwork();
            new MatsimNetworkReader(ptNetwork).readFile(ptNet);
            assignZone(ptNetwork);
            Map<Id<Link>, Double> allCarVolumes = readCarVolumes(carVolumesFile);
            List<Link> relevantPtLinks = ptNetwork.getLinks().values().stream().filter(link -> (double) link.getAttributes().getAttribute(IS_IN) > 0.0).collect(Collectors.toList());
            String attribute = runId + "_ptVolume";
            System.out.println(attribute);
            double tramVolume = relevantPtLinks.stream().filter(link -> link.getAllowedModes().contains(SBBModes.PTSubModes.TRAM)).mapToDouble(link -> getPKM(attribute, link)).sum();
            double tramKM = getAKM(departuresPerLink, relevantPtLinks, SBBModes.PTSubModes.TRAM);

            double busVolume = relevantPtLinks.stream().filter(link -> link.getAllowedModes().contains(SBBModes.PTSubModes.BUS)).mapToDouble(link -> getPKM(attribute, link)).sum();
            double busKM = getAKM(departuresPerLink, relevantPtLinks, SBBModes.PTSubModes.BUS);
            double trainVolume = relevantPtLinks.stream().filter(link -> link.getAllowedModes().contains(SBBModes.PTSubModes.RAIL)).mapToDouble(link -> getPKM(attribute, link)).sum();
            double trainKM = getAKM(departuresPerLink, relevantPtLinks, SBBModes.PTSubModes.RAIL);
            double otherVolume = relevantPtLinks.stream().filter(link -> link.getAllowedModes().contains(SBBModes.PTSubModes.OTHER)).mapToDouble(link -> getPKM(attribute, link)).sum();
            double otherKM = getAKM(departuresPerLink, relevantPtLinks, SBBModes.PTSubModes.OTHER);
            double carVolumes = relevantCarLinks.stream().mapToDouble(link -> allCarVolumes.getOrDefault(link.getId(), 0.0) * link.getLength() * 0.001).sum();
            RunVolumeData runVolume = new RunVolumeData(runId, carVolumes, tramVolume, trainVolume, otherVolume, busVolume, trainKM, busKM, tramKM, otherKM);
            runVolumeDataMap.put(runId, runVolume);
        }
    }

    private Map<Id<Link>, Map<String, Integer>> calcDeparturesPerLink(TransitSchedule transitSchedule) {
        Map<Id<Link>, Map<String, Integer>> result = new HashMap<>();
        for (TransitLine line : transitSchedule.getTransitLines().values()) {
            for (TransitRoute route : line.getRoutes().values()) {
                int departures = route.getDepartures().size();
                for (var linkId : route.getRoute().getLinkIds()) {
                    Map<String, Integer> departuresOnLink = result.computeIfAbsent(linkId, l -> new HashMap<>());
                    int modeDepartures = departuresOnLink.getOrDefault(route.getTransportMode(), 0) + departures;
                    departuresOnLink.put(route.getTransportMode(), modeDepartures);
                }
            }
        }
        return result;
    }

    record RunVolumeData(String run, double carVolume, double tramVolume, double trainVolume, double otherVolume,
                         double busVolume, double railAKM, double busAKM, double tramAKM, double otherAKM) {
    }

}
