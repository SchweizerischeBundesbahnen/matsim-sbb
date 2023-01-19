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
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
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

    static String transitFile = "Z:/99_Playgrounds/MD/Umlegung/Old/transitSchedule2020.xml.gz";
    static String networkFile = "Z:/99_Playgrounds/MD/Umlegung/Old/transitNetwork2020.xml.gz";
    static String outputTransitFile = "C:/devsbb/writeFilePlace/Umlegung/railTransitSchedule2020.xml.gz";
    static String outputNetworkFile = "C:/devsbb/writeFilePlace/Umlegung/railTransitNetwork2020.xml.gz";
    static String demandStationsFile = "Z:/99_Playgrounds/MD/Umlegung/Input/ColumNames.csv";

    private FilterPT() {
    }

    public static void main(String[] args) {

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        new TransitScheduleReader(scenario).readFile(transitFile);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);

        filterSchedual(scenario);

        System.out.println("Done");
    }

    public static void filterSchedual(Scenario scenario) {

        //List<Integer> codeList = readDemandStationsFile();

        Set<TransitStopFacility> transitStopFacilities = new LinkedHashSet<>();
        List<TransitLine> transitLines = scenario.getTransitSchedule().getTransitLines().values().stream()
            .filter(transitLine -> transitLine.getRoutes().values().stream().anyMatch(transitRoute -> transitRoute.getTransportMode().equals(PTSubModes.RAIL)))
            .map(transitLine -> {
                transitLine.getRoutes().values().forEach(transitRoute ->
                {transitRoute.getStops().forEach(transitRouteStop -> transitStopFacilities.add(transitRouteStop.getStopFacility()));});
                return transitLine;})
            .toList();


        Scenario smallScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        transitStopFacilities.forEach(transitStopFacility -> smallScenario.getTransitSchedule().addStopFacility(transitStopFacility));
        transitLines.forEach(transitLine -> smallScenario.getTransitSchedule().addTransitLine(transitLine));

        NetworkFilterManager networkFilterManager = new NetworkFilterManager(scenario.getNetwork(), new NetworkConfigGroup());
        networkFilterManager.addLinkFilter(l-> l.getAllowedModes().contains(PTSubModes.RAIL) || l.getFromNode().equals(l.getToNode()) &&
            l.getFromNode().getInLinks().values().stream().anyMatch(link -> link.getAllowedModes().contains(PTSubModes.RAIL)));
        Network network = networkFilterManager.applyFilters();
        //org.matsim.core.network.algorithms.NetworkCleaner networkCleaner = new org.matsim.core.network.algorithms.NetworkCleaner();
        //networkCleaner.run(network);

        removeUnnessesaryMinimalTransferTimes(scenario, smallScenario);

        new TransitScheduleWriter(smallScenario.getTransitSchedule()).writeFile(outputTransitFile);
        new NetworkWriter(network).write(outputNetworkFile);

        filterMATSimVisumLinks(network);
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

        String linksConnrctionFile = "Z:/99_Playgrounds/MD/Umlegung/Old/link_sequences2020.csv";
        String polylines = "Z:/99_Playgrounds/MD/Umlegung/Old/polylines2020.csv";
        String outputSaveFile = "C:/devsbb/writeFilePlace/Umlegung/saveFile2020.csv";

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
