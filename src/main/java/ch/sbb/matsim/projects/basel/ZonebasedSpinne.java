package ch.sbb.matsim.projects.basel;

import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailTripsAnalyzer;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.routing.SBBAnalysisMainModeIdentifier;
import ch.sbb.matsim.zones.*;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.HasPlansAndId;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class ZonebasedSpinne {


    public static final String ZZZZ_OUTSIDE = "zzzz_outside";
    private final Population population;
    private final TransitSchedule transitSchedule;
    private final RailTripsAnalyzer railTripsAnalyzer;
    private final SBBAnalysisMainModeIdentifier mainModeIdentifier;
    private final ArrayList<String> zonesList;
    private final Zones zones;
    private final double multiplicator;
    private final Network network;

    public static void main(String[] args) {
        String runprefix = args[0];
        String networkFile = runprefix + "output_network.xml.gz";
        String transitScheduleFile = runprefix + "output_transitSchedule.xml.gz";
        String experiencedPlansFile = runprefix + "output_experienced_plans.xml.gz";
        String zonesFile = args[1];
        String outputFolder = args[2];
        double multiplicator = Double.parseDouble(args[3]);
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);
        new TransitScheduleReader(scenario).readFile(transitScheduleFile);
        Zones zones = ZonesLoader.loadZones("zones",zonesFile,"id");
        new PopulationReader(scenario).readFile(experiencedPlansFile);
        var spinne = new ZonebasedSpinne(scenario,zones,multiplicator);
        spinne.calculateOdMatrix(SBBModes.PT,"Oev Nachfragematrix",outputFolder+"/pt_demand.csv");
        spinne.calculatePTMatrixWithFilter(spinne.getAllLinkIdsBetweenTwoNodes(Id.createNodeId("pt_41001158"),Id.createNodeId("pt_41001182")),"St.Johann<->Voltaplatz",outputFolder+"/st.johann-voltaplatz.csv");
        spinne.calculatePTMatrixWithFilter(spinne.getAllLinkIdsBetweenTwoNodes(Id.createNodeId("pt_19054489"),Id.createNodeId("pt_100001204")),"Basel Mitte<->Basel SBB (Tief)",outputFolder+"/basel_sbb_basel_mitte.csv");
        spinne.calculatePTMatrixWithFilter(spinne.getAllLinkIdsBetweenTwoNodes(Id.createNodeId("pt_19054489"),Id.createNodeId("pt_1397")),"Basel Mitte<->Basel St. Johann",outputFolder+"/basel_mitte_basel_stjohann.csv");
        spinne.calculatePTMatrixWithFilter(spinne.getAllLinkIdsBetweenTwoNodes(Id.createNodeId("pt_19054489"),Id.createNodeId("pt_19054490")),"Basel Mitte<->Basel Klybeck",outputFolder+"/basel_mitte_basel_klybeck.csv");
        spinne.calculatePTMatrixWithFilter(spinne.getAllLinkIdsBetweenTwoNodes(Id.createNodeId("pt_100001203"),Id.createNodeId("pt_19054490")),"Basel Bad Bf (Tief)<->Basel Klybeck",outputFolder+"/basel_bad_basel_klybeck.csv");
        spinne.calculatePTMatrixWithFilter(spinne.getAllLinkIdsBetweenTwoNodes(Id.createNodeId("pt_19054496"),Id.createNodeId("pt_1388")),"Basel SBB<->Basel Morgartenring",outputFolder+"/basel_sbb_basel_morgartenring.csv");
        spinne.calculatePTMatrixWithFilter(spinne.getAllLinkIdsBetweenTwoNodes(Id.createNodeId("pt_41001301"),Id.createNodeId("pt_41001317")),"Basel, Badischer Bahnhof<->Basel, Gewerbeschule",outputFolder+"/basel_bad_gewerbeschule.csv");
    }

    private Set<Id<Link>> getAllLinkIdsBetweenTwoNodes(Id<Node> firstNode, Id<Node> secondNode) {
        Node fromNode = network.getNodes().get(firstNode);
        Set<Id<Link>> linkIds = new HashSet<>();
        linkIds.addAll(fromNode.getOutLinks().values().stream().filter(link -> link.getToNode().getId().equals(secondNode)).map(link -> link.getId()).collect(Collectors.toSet()));
        linkIds.addAll(fromNode.getInLinks().values().stream().filter(link -> link.getFromNode().getId().equals(secondNode)).map(link -> link.getId()).collect(Collectors.toSet()));
        return linkIds;
    }


    ZonebasedSpinne(Scenario scenario, Zones zones, double multiplicator){
        this.population = scenario.getPopulation();
        this.multiplicator = multiplicator;
        this.transitSchedule = scenario.getTransitSchedule();
        this.zones = zones;
        this.network = scenario.getNetwork();
        ZonesCollection zonesCollection = new ZonesCollection();
        zonesCollection.addZones(zones);
        this.railTripsAnalyzer = new RailTripsAnalyzer(scenario.getTransitSchedule(),scenario.getNetwork(),zonesCollection);
        this.mainModeIdentifier = new SBBAnalysisMainModeIdentifier();
        this.zonesList = ((ZonesImpl)zones).getZones().stream().map(zone -> String.valueOf(zone.getAttribute("Name"))).sorted().collect(Collectors.toCollection(ArrayList::new));
        this.zonesList.add(ZZZZ_OUTSIDE);
    }
    public void calculateOdMatrix(String mode, String comment, String outputFile){
        int[][] matrix = new int[zonesList.size()][zonesList.size()];
        this.population.getPersons().values()
                .stream()
                .map(HasPlansAndId::getSelectedPlan)
                .flatMap(plan -> TripStructureUtils.getTrips(plan).stream())
                .filter(trip -> this.mainModeIdentifier.identifyMainMode(trip.getTripElements()).equals(mode))
                .map(trip -> getToFromZone(trip))
                .forEach(odTuple ->
                    matrix[zonesList.indexOf(odTuple.getFirst())][zonesList.indexOf(odTuple.getSecond())]++
                );
        writeMatrix(matrix,comment,outputFile);

    }
    private void calculatePTMatrixWithFilter(Set<Id<Link>> linkFilter, String comment, String outputfile ) {
        int[][] matrix = new int[zonesList.size()][zonesList.size()];
        this.population.getPersons().values()
                .stream()
                .map(HasPlansAndId::getSelectedPlan)
                .flatMap(plan -> TripStructureUtils.getTrips(plan).stream())
                .filter(trip -> this.mainModeIdentifier.identifyMainMode(trip.getTripElements()).equals(SBBModes.PT))
                .filter(trip -> hasTraveledOnAnyLink(trip,linkFilter))
                .map(trip -> getToFromZone(trip))
                .forEach(odTuple ->
                        matrix[zonesList.indexOf(odTuple.getFirst())][zonesList.indexOf(odTuple.getSecond())]++
                );
        writeMatrix(matrix,comment,outputfile);
    }

    private boolean hasTraveledOnAnyLink(TripStructureUtils.Trip trip, Set<Id<Link>> linkFilter) {
        return  trip.getLegsOnly()
                .stream()
                .filter(leg -> leg.getMode().equals(SBBModes.PT))
                .map(leg -> (TransitPassengerRoute)leg.getRoute())
                .flatMap(transitPassengerRoute -> railTripsAnalyzer.getPtLinkIdsTraveledOn(transitPassengerRoute).stream())
                .anyMatch(linkId -> linkFilter.contains(linkId));

    }

    private void writeMatrix(int[][] matrix, String comment, String outputFile) {
        List<String> header = new ArrayList<>();
        header.add("von");
        header.addAll(zonesList);
        try (CSVWriter writer = new CSVWriter(comment+"\n", header.toArray(new String[header.size()]), outputFile)) {
            for (int from = 0; from < zonesList.size(); from++) {
                String currentFrom = zonesList.get(from);
                writer.set("von", currentFrom);
                for (int to = 0; to < zonesList.size(); to++) {
                    writer.set(zonesList.get(to), String.valueOf(Math.round(multiplicator * matrix[from][to])));
                }
                writer.writeRow();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private Tuple<String, String> getToFromZone(TripStructureUtils.Trip trip) {
        Zone originZone = zones.findZone(trip.getOriginActivity().getCoord());
        Zone destinationZone = zones.findZone(trip.getDestinationActivity().getCoord());
        String originZoneName = originZone!=null?String.valueOf(originZone.getAttribute("Name")):ZZZZ_OUTSIDE;
        String destinationZoneName = destinationZone!=null?String.valueOf(destinationZone.getAttribute("Name")):ZZZZ_OUTSIDE;
        return new Tuple<>(originZoneName,destinationZoneName);
    }
}
