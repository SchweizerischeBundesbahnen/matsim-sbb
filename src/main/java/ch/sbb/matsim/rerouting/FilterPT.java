package ch.sbb.matsim.rerouting;

import ch.sbb.matsim.config.variables.SBBModes.PTSubModes;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.NetworkConfigGroup;
import org.matsim.core.network.filter.NetworkFilterManager;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.MinimalTransferTimes;
import org.matsim.pt.transitSchedule.api.MinimalTransferTimes.MinimalTransferTimesIterator;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public final class FilterPT {

    static String transitFile = "Z:/99_Playgrounds/MD/Umlegung/Old/2018/transitSchedule.xml.gz";
    static String networkFile = "Z:/99_Playgrounds/MD/Umlegung/Old/2018/transitNetwork.xml.gz";
    static String outputTransitFile = "transitSchedule.xml.gz";
    static String outputNetworkFile = "transitNetwork.xml.gz";
    static String demandStationsFile = "";

    private FilterPT() {
    }

    public static void main(String[] args) {

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        new TransitScheduleReader(scenario).readFile(transitFile);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);
        for (Node n : scenario.getNetwork().getNodes().values()) {
            Coord coord = n.getCoord();
            Coord newCoord = new Coord(n.getCoord().getX() + (2600050.78090007 - 2599978.43312236), n.getCoord().getY() + (1199824.09952451 - 1199677.02592021));
            //			coord.setXY(new_coord.getX(), new_coord.getY());
            n.setCoord(newCoord);
        }
        NetworkFactory nf = scenario.getNetwork().getFactory();
        /*for (TransitStopFacility transitStopFacility : scenario.getTransitSchedule().getFacilities().values()) {
            if (!scenario.getNetwork().getLinks().containsKey(Id.createLinkId(transitStopFacility.getId()))) {
                Link link = nf.createLink(Id.createLinkId(transitStopFacility.getId()), scenario.getNetwork().getNodes().get(Id.createNodeId(transitStopFacility.getId())),scenario.getNetwork().getNodes().get(Id.createNodeId(transitStopFacility.getId())));
                link.setLength(0.1);
                link.setFreespeed(10000);
                link.setCapacity(10000);
                link.setNumberOfLanes(10000);
                link.setAllowedModes(Set.of("pt"));
                scenario.getNetwork().addLink(link);
            }
        }*/

        filterSchedual(scenario);

        System.out.println("Done");
    }

    public static void filterSchedual(Scenario scenario) {

        Set<TransitStopFacility> transitStopFacilities = new LinkedHashSet<>();

        // load scenario from 2017 to find "Bergbahnen" und "Trams"
        Scenario scenarioReduction = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenarioReduction).readFile("Z:/99_Playgrounds/MD/Umlegung/Old/2017/transitSchedule.xml.gz");

        // find all rail lines
        List<TransitLine> transitLinesReduction = scenarioReduction.getTransitSchedule().getTransitLines().values().stream()
            .filter(transitLine -> transitLine.getRoutes().values().stream().anyMatch(transitRoute -> transitRoute.getTransportMode().equals(PTSubModes.RAIL)))
            .toList();

        // if rail line then safe the all stops
        List<TransitLine> transitLines = scenario.getTransitSchedule().getTransitLines().values().stream().filter(transitLinesReduction::contains).
            map(transitLine -> {transitLine.getRoutes().values().forEach(transitRoute ->
            {
                transitRoute.getStops().forEach(transitRouteStop -> transitStopFacilities.add(transitRouteStop.getStopFacility()));
            });
            return transitLine;
        }).toList();

        // create the reduced scenario, adding all stops and lines
        Scenario smallScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        transitStopFacilities.forEach(transitStopFacility -> smallScenario.getTransitSchedule().addStopFacility(transitStopFacility));
        transitLines.forEach(transitLine -> smallScenario.getTransitSchedule().addTransitLine(transitLine));

        // find all links with rail
        NetworkFilterManager networkFilterManagerReduction = new NetworkFilterManager(scenario.getNetwork(), new NetworkConfigGroup());
        networkFilterManagerReduction.addLinkFilter(l -> !l.getAllowedModes().contains(PTSubModes.RAIL) || !l.getFromNode().equals(l.getToNode()) &&
            !l.getFromNode().getInLinks().values().stream().anyMatch(link -> link.getAllowedModes().contains(PTSubModes.RAIL)));
        Network networkReduction = networkFilterManagerReduction.applyFilters();

        // filter, remove all none rail links
        NetworkFilterManager networkFilterManager = new NetworkFilterManager(scenario.getNetwork(), new NetworkConfigGroup());
        networkFilterManager.addLinkFilter(l -> !networkReduction.getLinks().containsKey(l.getId()));
        Network network = networkFilterManager.applyFilters();
        //org.matsim.core.network.algorithms.NetworkCleaner networkCleaner = new org.matsim.core.network.algorithms.NetworkCleaner();
        //networkCleaner.run(network);

        removeUnnessesaryMinimalTransferTimes(scenario, smallScenario);

        new TransitScheduleWriter(smallScenario.getTransitSchedule()).writeFile(outputTransitFile);
        new NetworkWriter(network).write(outputNetworkFile);

        filterMATSimVisumLinks(network);
    }

    private static Scenario readFileForReduction() {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(outputTransitFile);
        return scenario;
    }

    private static List<Integer> readDemandStationsFile() {
        List<Integer> codeList = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(demandStationsFile))) {
            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            while ((line = reader.readLine()) != null) {
                codeList.add(Integer.parseInt(line.split(";")[header.indexOf("id")]));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return codeList;
    }

    private static void removeUnnessesaryMinimalTransferTimes(Scenario scenario, Scenario smallScenario) {
        MinimalTransferTimesIterator iterator = scenario.getTransitSchedule().getMinimalTransferTimes().iterator();
        MinimalTransferTimes minimalTransferTimes = smallScenario.getTransitSchedule().getMinimalTransferTimes();

        while (iterator.hasNext()) {
            iterator.next();
            if (smallScenario.getTransitSchedule().getFacilities().containsKey(iterator.getFromStopId())
                && smallScenario.getTransitSchedule().getFacilities().containsKey(iterator.getToStopId())) {
                minimalTransferTimes.set(iterator.getFromStopId(), iterator.getToStopId(), iterator.getSeconds());
            }
        }
    }

    private static void filterMATSimVisumLinks(Network network) {

        String linksConnrctionFile = "Z:/99_Playgrounds/MD/Umlegung/Old/2018/link_sequences.csv";
        String polylines = "Z:/99_Playgrounds/MD/Umlegung/Old/2018/polylines.csv";
        String outputSaveFile = "saveFile.csv";

        Map<Id<Link>, DemandStorage> idDemandStorageMap = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(linksConnrctionFile))) {

            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            while ((line = reader.readLine()) != null) {
                var linkId = Id.createLinkId(line.split(";")[header.indexOf("matsim_link")]);
                if (network.getLinks().containsKey(linkId)) {
                    idDemandStorageMap.put(linkId, new DemandStorage(linkId, line.split(";")[header.indexOf("link_sequence_visum")]));
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(polylines))) {

            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            while ((line = reader.readLine()) != null) {
                var linkId = Id.createLinkId(line.split(";")[header.indexOf("LINK")]);
                if (idDemandStorageMap.containsKey(linkId)) {
                    idDemandStorageMap.get(linkId).setWkt(line.split(";")[header.indexOf("WKT")]);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputSaveFile))) {

            writer.write("Matsim_Link;Demand;Visum_Link;WKT");
            writer.newLine();
            for (DemandStorage demandStorage : idDemandStorageMap.values()) {
                writer.write(demandStorage.toString());
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
