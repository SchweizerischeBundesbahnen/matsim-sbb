package ch.sbb.matsim.projects.genf2050;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import com.google.common.util.concurrent.AtomicDouble;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.tabularFileParser.TabularFileParser;
import org.matsim.core.utils.io.tabularFileParser.TabularFileParserConfig;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;


public class AreaAnalysis {
    public static final String IS_IN = "isIn";
    private final Map<String, String> runs;
    private final Network roadNetwork;
    private final Zones zones;
    private final static Logger log = LogManager.getLogger(AreaAnalysis.class);
    final Map<String, RunVolumeData> runVolumeDataMap = new TreeMap<>();


    public AreaAnalysis(Network roadNetwork, Zones zones, Map<String, String> runs) {
        this.roadNetwork = roadNetwork;
        this.zones = zones;
        this.runs = runs;
        assignZone(roadNetwork);

    }

    public static void main(String[] args) {
        Map<String, String> runs = new TreeMap<>();
        String root = "/Volumes/mobi/40_Projekte/20251005_GE_2050_plus/sim/";

        //runs.put("ref_runde_2", root + "ref_runde_2");
        runs.put("var_cornavin_re_metro_as_other_jun26", root + "var_cornavin_re_metro_as_other_jun26");
        //runs.put("var_cornavin_metro_as_other", root + "var_cornavin_metro_as_other");
        //runs.put("var_eaux_vives_re_metro_as_other", root + "var_eaux_vives_re_metro_as_other");

        String roadNet = "/Volumes/mobi/40_Projekte/20251005_GE_2050_plus/streets/output/network.xml.gz";
        String zonesFile = "/Volumes/mobi/40_Projekte/20251005_GE_2050_plus/Auswertungen/kanton_GE.shp";
        String outputFile = "/Volumes/mobi/40_Projekte/20251005_GE_2050_plus/Auswertungen/perimeter-ge3.csv";
        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(roadNet);
        Zones zones = ZonesLoader.loadZones(Variables.ZONE_ID, zonesFile);
        AreaAnalysis areaAnalysis = new AreaAnalysis(network, zones, runs);
        areaAnalysis.run(0.25);
        areaAnalysis.write(outputFile);
    }

    private static double getAKM(Map<Id<Link>, Map<String, Integer>> departuresPerLink, List<Link> relevantPtLinks, String mode) {

        return relevantPtLinks.stream().filter(link -> link.getAllowedModes().contains(mode))
                .mapToDouble(link -> departuresPerLink.getOrDefault(link.getId(), Collections.emptyMap())
                .getOrDefault(mode, 0) * link.getLength() * (double) link.getAttributes().getAttribute(IS_IN) * 0.001)
                .sum();
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
            writer.set("means", "metro");
            for (RunVolumeData d : runVolumeDataMap.values()) {
                writer.set(d.run, Double.toString(d.metrovolume()));
            }
            writer.writeRow();
            writer.set("means", SBBModes.PTSubModes.OTHER);
            for (RunVolumeData d : runVolumeDataMap.values()) {
                writer.set(d.run, Double.toString(d.otherVolume()));
            }

            writer.writeRow();
            writer.set("means", SBBModes.WALK_MAIN_MAINMODE);
            for (RunVolumeData d : runVolumeDataMap.values()) {
                writer.set(d.run, Double.toString(d.walkVolume()));
            }

            writer.writeRow();
            writer.set("means", SBBModes.BIKE);
            for (RunVolumeData d : runVolumeDataMap.values()) {
                writer.set(d.run, Double.toString(d.bikeVolume()));
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
            writer.set("means", "metro");
            for (RunVolumeData d : runVolumeDataMap.values()) {
                writer.set(d.run, Double.toString(d.metroAKM()));
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

    private void run(double samplesize) {
        List<Link> relevantCarLinks = roadNetwork.getLinks().values().stream().filter(link -> (double) link.getAttributes().getAttribute(IS_IN) > 0.0).collect(Collectors.toList());
        for (var run : runs.entrySet()) {
            String runId = run.getKey();
            log.info("Processing run " +  runId);
            String path = run.getValue();
            String ptNet = path + "/output/" + runId + ".ptlinkvolumes.att_network.xml.gz";
            String scheduleFile = path + "/output/" + runId + ".output_transitSchedule.xml.gz";
            String carVolumesFile = path + "/output/" + runId + ".car_volumes.att";
            String outputPopulation = path + "/output/" + runId + ".output_plans.xml.gz";
            Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
            new TransitScheduleReader(scenario).readFile(scheduleFile);
            Set<Id<Link>> metroLinkIds = scenario.getTransitSchedule().getTransitLines().values().stream()
                    .filter(line -> line.getId().toString().equals("M1_EV"))
                    .flatMap(line -> line.getRoutes().values().stream())
                    .flatMap(route -> route.getRoute().getLinkIds().stream())
                    .collect(Collectors.toSet());

            var departuresPerLink = calcDeparturesPerLink(scenario.getTransitSchedule());
            Network ptNetwork = scenario.getNetwork();
            new MatsimNetworkReader(ptNetwork).readFile(ptNet);
            assignZone(ptNetwork);

            log.info("Reading car volumes from " + carVolumesFile);
            Map<Id<Link>, Double> allCarVolumes = readCarVolumes(carVolumesFile);
            List<Link> relevantPtLinks = ptNetwork.getLinks().values().stream().filter(link -> (double) link.getAttributes().getAttribute(IS_IN) > 0.0).collect(Collectors.toList());
            String attribute = "VOLUME";
            System.out.println(attribute);
            new PopulationReader(scenario).readFile(outputPopulation);
            log.info("Processing walk and bike...");
            List<String> slowModes = List.of(SBBModes.WALK_MAIN_MAINMODE, SBBModes.BIKE);
            AtomicDouble walkKM = new AtomicDouble();
            AtomicDouble bikeKM = new AtomicDouble();
            scenario.getPopulation().getPersons().values()
                    .parallelStream()
                    .map(person -> person.getSelectedPlan())
                    .flatMap(plan -> TripStructureUtils.getTrips(plan).stream())
                    .filter(trip -> trip.getLegsOnly().stream().anyMatch(leg -> slowModes.contains(leg.getMode())))
                    .filter(trip -> zones.findZone(trip.getOriginActivity().getCoord()) != null && zones.findZone(trip.getDestinationActivity().getCoord()) != null)
                    .forEach(trip -> {
                        for (var leg : trip.getLegsOnly()) {
                            if (leg.getMode().equals(SBBModes.WALK_MAIN_MAINMODE)) {
                                walkKM.addAndGet(leg.getRoute().getDistance()/samplesize);
                            } else if (leg.getMode().equals(SBBModes.BIKE)) {
                                bikeKM.addAndGet(leg.getRoute().getDistance()/samplesize);
                            }
                        }
                    });

            log.info("Calculating volumes for PT...");
            List<Link> trueTramLinks = relevantPtLinks.stream().filter(link -> link.getAllowedModes()
                            .contains(SBBModes.PTSubModes.TRAM))
                            .filter(link -> !metroLinkIds.contains(link.getId()))
                            .collect(Collectors.toList());
            double tramVolume = trueTramLinks.stream()
                    .mapToDouble(link -> getPKM(attribute, link)).sum();
            List<Link> metroLinks = relevantPtLinks.stream()
                    .filter(link -> metroLinkIds.contains(link.getId()))
                    .collect(Collectors.toList());
            double metroVolume = metroLinks.stream()
                    .mapToDouble(link -> getPKM(attribute, link)).sum();
            double tramKM = getAKM(departuresPerLink, trueTramLinks, SBBModes.PTSubModes.TRAM);
            double metroKM = metroLinks.isEmpty()?0.0:getAKM(departuresPerLink, metroLinks, SBBModes.PTSubModes.TRAM);
            if (metroKM == 0.0) {
                metroKM = metroLinks.isEmpty()?0.0:getAKM(departuresPerLink, metroLinks, SBBModes.PTSubModes.OTHER);
            }

            double busVolume = relevantPtLinks.stream().filter(link -> link.getAllowedModes().contains(SBBModes.PTSubModes.BUS)).mapToDouble(link -> getPKM(attribute, link)).sum();
            double busKM = getAKM(departuresPerLink, relevantPtLinks, SBBModes.PTSubModes.BUS);
            double trainVolume = relevantPtLinks.stream().filter(link -> link.getAllowedModes().contains(SBBModes.PTSubModes.RAIL)).mapToDouble(link -> getPKM(attribute, link)).sum();
            double trainKM = getAKM(departuresPerLink, relevantPtLinks, SBBModes.PTSubModes.RAIL);
            double otherVolume = relevantPtLinks.stream().filter(link -> link.getAllowedModes().contains(SBBModes.PTSubModes.OTHER)).mapToDouble(link -> getPKM(attribute, link)).sum();
            double otherKM = getAKM(departuresPerLink, relevantPtLinks, SBBModes.PTSubModes.OTHER);
            double carVolumes = relevantCarLinks.stream().mapToDouble(link -> allCarVolumes.getOrDefault(link.getId(), 0.0) * link.getLength() * 0.001).sum();
            RunVolumeData runVolume = new RunVolumeData(runId, carVolumes, tramVolume, trainVolume, otherVolume, busVolume,metroVolume, trainKM, busKM, tramKM, metroKM, otherKM, bikeKM.get() / 1000.0, walkKM.get() / 1000.0);
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
                         double busVolume, double metrovolume, double railAKM, double busAKM, double tramAKM, double metroAKM,  double otherAKM,
                         double bikeVolume, double walkVolume) {
    }

}
