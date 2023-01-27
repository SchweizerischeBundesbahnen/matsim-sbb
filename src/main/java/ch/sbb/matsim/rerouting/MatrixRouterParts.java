package ch.sbb.matsim.rerouting;

import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailTripsAnalyzer;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet;
import ch.sbb.matsim.routing.pt.raptor.AccessEgressRouteCache;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorInVehicleCostCalculator;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorParametersForPerson;
import ch.sbb.matsim.routing.pt.raptor.DefaultRaptorTransferCostCalculator;
import ch.sbb.matsim.routing.pt.raptor.LeastCostRaptorRouteSelector;
import ch.sbb.matsim.routing.pt.raptor.RaptorInVehicleCostCalculator;
import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import ch.sbb.matsim.routing.pt.raptor.RaptorParametersForPerson;
import ch.sbb.matsim.routing.pt.raptor.RaptorRoute.RoutePart;
import ch.sbb.matsim.routing.pt.raptor.RaptorRouteSelector;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig.RaptorOptimization;
import ch.sbb.matsim.routing.pt.raptor.RaptorTransferCostCalculator;
import ch.sbb.matsim.routing.pt.raptor.RaptorUtils;
import ch.sbb.matsim.routing.pt.raptor.SBBIntermodalRaptorStopFinder;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorCore.TravelInfo;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import com.sun.source.util.Trees;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup.ModeParams;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.DefaultRoutingRequest;
import org.matsim.core.router.RoutingRequest;
import org.matsim.core.router.SingleModeNetworksCache;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacilitiesFactory;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.Facility;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class MatrixRouterParts {

    List<Integer> startId = List.of();//80
    List<Integer> endId = List.of(478);

    final static String YEAR = "2020";
    final static String TRANSIT = "rail";
    final static String TRY = "tree";
    final static String columNames = "Z:/99_Playgrounds/MD/Umlegung/Input/ZoneToNode.csv";
    final static String demand = "Z:/99_Playgrounds/MD/Umlegung/Input/Visum/Demand2018.omx";
    final static String saveFileInpout = "Z:/99_Playgrounds/MD/Umlegung/Input/" + YEAR + "/" + TRANSIT + "/saveFile.csv";
    final static String schedualFile = "Z:/99_Playgrounds/MD/Umlegung/Input/" + YEAR + "/" + TRANSIT + "/transitSchedule.xml.gz";
    final static String netwoekFile = "Z:/99_Playgrounds/MD/Umlegung/Input/" + YEAR + "/" + TRANSIT + "/transitNetwork.xml.gz";
    final static String output = "Z:/99_Playgrounds/MD/Umlegung/Results/" + YEAR + "/" + TRANSIT + "/" + TRY + ".csv";

    //final static String outputRoutes = "Z:/99_Playgrounds/MD/Umlegung/Results/" + YEAR + "/" + TRANSIT + "/" + TRY + "Routes.csv";
    final static String outputRoutes = TRY + "Routes.csv";

    final InputDemand inputDemand;
    final Map<Id<Link>, DemandStorage> idDemandStorageMap = createLinkDemandStorage();
    final ActivityFacilitiesFactory afFactory = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getActivityFacilities().getFactory();
    final Config config;
    final Scenario scenario;
    final SwissRailRaptor swissRailRaptor;
    final RailTripsAnalyzer railTripsAnalyzer;
    final SwissRailRaptorData data;

    static int count = 0;
    static double missingDemand = 0;
    static double routedDemand = 0;

    SBBIntermodalRaptorStopFinder stopFinder;
    RaptorParametersForPerson raptorParametersForPerson;
    RaptorRouteSelector routeSelector = new LeastCostRaptorRouteSelector();
    RaptorInVehicleCostCalculator inVehicleCostCalculator = new DefaultRaptorInVehicleCostCalculator();
    RaptorTransferCostCalculator transferCostCalculator = new DefaultRaptorTransferCostCalculator();
    static List<String> lines = new ArrayList<>();
    Queue<Integer> ids = new LinkedList<>();

    public static void main(String[] args) {
        long startTime = System.nanoTime();
        lines.add("PATHINDEX;PATHLEGINDEX;FROMSTOPPOINTNO;TOSTOPPOINTNO;DEPTIME;ARRTIME");
        MatrixRouterParts matrixRouter = new MatrixRouterParts();
        for (int i = 1; i < 100000000; i++) {
            matrixRouter.ids.add(i);
        }
        System.out.println("MatrixRouter: " + ((System.nanoTime() - startTime) / 1_000_000_000) + "s");
        matrixRouter.route();
        System.out.println("It took " + ((System.nanoTime() - startTime) / 1_000_000_000) + "s");
        System.out.println("Missing connections: " + count);
        System.out.println("Missing demand from connections: " + missingDemand);
        System.out.println("Missing demand from stations: " + matrixRouter.inputDemand.getMissingDemand());
        System.out.println("Routed demand: " + routedDemand);
        //matrixRouter.calculateTest();
    }

    private void route() {
        if (startId.size() != 0 && endId.size() != 0) {
            System.out.println("Point to Point");
            routingPointToPointTree(startId, endId);
            //routingPointToPointCalc(startId, endId);
        } else if (TRY.equals("tree")) {
            inputDemand.getTimeList().stream().parallel().forEach(this::calculateTree);
        } else if (TRY.contains("calc")) {
            inputDemand.getTimeList().stream().parallel().forEach(this::calculateMatrix);
        }
        writeLinkCount();
        writeRoute();
    }

    private void writeRoute() {

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputRoutes))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private void routingPointToPointCalc(List<Integer> startId, List<Integer> endId) {
        inputDemand.getTimeList().stream().parallel().forEach(time -> calculatePoint(time, this.startId, this.endId));
        writeLinkCount();
    }

    private void calculatePoint(Integer time, List<Integer> startId, List<Integer> endId) {
        long startTime = System.nanoTime();
        var raptor = new SwissRailRaptor(data, raptorParametersForPerson, routeSelector, stopFinder, inVehicleCostCalculator, transferCostCalculator);
        double[][] matrix = (double[][]) inputDemand.getOmxFile().getMatrix(time.toString()).getData();
        for (Entry<Integer, Coord> entryX : inputDemand.getValidPosistions().entrySet()) {
            if (!startId.contains(entryX.getKey() + 1)) {
                continue;
            }
            for (Entry<Integer, Coord> entryY : inputDemand.getValidPosistions().entrySet()) {
                if (!endId.contains(entryY.getKey() + 1)) {
                    continue;
                }
                double timeDemand = matrix[entryX.getKey()][entryY.getKey()];
                if (timeDemand != 0) {
                    Facility startF = afFactory.createActivityFacility(Id.create(1, ActivityFacility.class), entryX.getValue());
                    Facility endF = afFactory.createActivityFacility(Id.create(2, ActivityFacility.class), entryY.getValue());
                    RoutingRequest request = DefaultRoutingRequest.withoutAttributes(startF, endF, (time - 1) * 600, null);
                    List<? extends PlanElement> legs = raptor.calcRoute(request);
                    if (legs == null) {
                        //System.out.println("No connection found for " + entryX.getValue() + " to " + entryX.getValue() + " at time " + time + " demand " + timeDemand);
                        //System.out.println("LINESTRING (" + entryX.getValue().getX() + " " + entryX.getValue().getY() + ", " + entryY.getValue().getX() + " " + entryY.getValue().getY() + ");" + time);
                        count++;
                        missingDemand += timeDemand;
                        continue;
                    }
                    routedDemand += timeDemand;
                    addDemand(timeDemand, legs);
                }
            }
        }
        System.out.println("Matrix: " + time + "; " + ((System.nanoTime() - startTime) / 1_000_000_000) + "s");
    }

    public void treeRouting() {
        inputDemand.getTimeList().stream().parallel().forEach(this::calculateTree);
        writeLinkCount();
    }

    public void routingPointToPointTree(List<Integer> startId, List<Integer> endId) {
        inputDemand.getTimeList().stream().parallel().forEach(time -> calculateTreePoint(time, startId, endId));
        writeLinkCount();
    }

    private void calculateTreePoint(Integer time, List<Integer> startId, List<Integer> endId) {
        long startTime = System.nanoTime();
        var raptor = new SwissRailRaptor(data, raptorParametersForPerson, routeSelector, stopFinder, inVehicleCostCalculator, transferCostCalculator);
        double[][] matrix = (double[][]) inputDemand.getOmxFile().getMatrix(time.toString()).getData();
        for (Entry<Integer, Coord> validPotion : inputDemand.getValidPosistions().entrySet()) {
            if (!startId.contains(validPotion.getKey() + 1)) {
                continue;
            }
            Facility startF = afFactory.createActivityFacility(Id.create(1, ActivityFacility.class), validPotion.getValue());
            Map<Id<TransitStopFacility>, TravelInfo> tree = raptor.calcTree(startF, (time - 1) * 600, null, null);
            for (Entry<Integer, Coord> destination : inputDemand.getValidPosistions().entrySet()) {
                if (!endId.contains(destination.getKey() + 1)) {
                    continue;
                }
                double timeDemand = matrix[validPotion.getKey()][destination.getKey()];
                if (timeDemand != 0) {
                    TravelInfo travelInfo = tree.get(data.findNearestStop(destination.getValue().getX(), destination.getValue().getY()).getId());
                    if (travelInfo == null) {
                        count++;
                        missingDemand += timeDemand;
                        continue;
                    }
                    routedDemand += timeDemand;
                    List<? extends PlanElement> legs = RaptorUtils.convertRouteToLegs(travelInfo.getRaptorRoute(),
                        ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class).getTransferWalkMargin());
                    addDemand(timeDemand, legs);
                }
            }
        }
        System.out.println("Matrix: " + time + "; " + ((System.nanoTime() - startTime) / 1_000_000_000) + "s");
    }

    private void calculateTree(Integer time) {
        long startTime = System.nanoTime();
        var raptor = new SwissRailRaptor(data, raptorParametersForPerson, routeSelector, stopFinder, inVehicleCostCalculator, transferCostCalculator);
        double[][] matrix = (double[][]) inputDemand.getOmxFile().getMatrix(time.toString()).getData();
        for (Entry<Integer, Coord> validPotion : inputDemand.getValidPosistions().entrySet()) {
            Facility startF = afFactory.createActivityFacility(Id.create(1, ActivityFacility.class), validPotion.getValue());
            Map<Id<TransitStopFacility>, TravelInfo> tree = raptor.calcTree(startF, (time - 1) * 600, null, null);
            for (Entry<Integer, Coord> destination : inputDemand.getValidPosistions().entrySet()) {
                double timeDemand = matrix[validPotion.getKey()][destination.getKey()];
                if (timeDemand != 0) {
                    TravelInfo travelInfo = tree.get(data.findNearestStop(destination.getValue().getX(), destination.getValue().getY()).getId());
                    if (travelInfo == null) {
                        count++;
                        missingDemand += timeDemand;
                        continue;
                    }
                    int pathlegindex = 1;
                    int pathindex = getID();
                    for (RoutePart routePart : travelInfo.getRaptorRoute().getParts()) {
                        if (routePart.mode.equals("pt")) {
                            addToLine(routePart, pathindex, pathlegindex++);
                        }
                    }
                    routedDemand += timeDemand;
                    List<? extends PlanElement> legs = RaptorUtils.convertRouteToLegs(travelInfo.getRaptorRoute(),
                        ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class).getTransferWalkMargin());
                    addDemand(timeDemand, legs);
                }
            }
        }
        System.out.println("Matrix: " + time + "; " + ((System.nanoTime() - startTime) / 1_000_000_000) + "s");
    }

    private synchronized int getID() {
        return ids.remove();
    }

    private synchronized void addToLine(RoutePart part, int pathindex, int pathlegindex) {
        lines.add(pathindex + ";" + pathlegindex + ";" + part.fromStop.getId() + ";" + part.toStop.getId() + ";" + (int) part.boardingTime + ";" + (int) part.arrivalTime);
    }

    private void addDemand(double timeDemand, List<? extends PlanElement> legs) {
        for (PlanElement pe : legs) {
            Leg leg = (Leg) pe;
            if (leg.getMode().equals("pt")) {
                List<Id<Link>> linkIds = railTripsAnalyzer.getPtLinkIdsTraveledOn((TransitPassengerRoute) leg.getRoute());
                for (Id<Link> linkId : linkIds) {
                    if (scenario.getNetwork().getLinks().get(linkId).getFromNode().equals(scenario.getNetwork().getLinks().get(linkId).getToNode())) {
                        continue;
                    }
                    if (idDemandStorageMap.containsKey(linkId)) {
                        idDemandStorageMap.get(linkId).increaseDemand(timeDemand);
                    } else {
                        System.out.println("Hilfe");
                        idDemandStorageMap.put(linkId, new DemandStorage(linkId));
                    }
                }
            }
        }
    }

    public MatrixRouterParts() {
        this.config = ConfigUtils.createConfig();

        SwissRailRaptorConfigGroup srrConfig = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class);
        List<IntermodalAccessEgressParameterSet> intermodalAccessEgressParameterSets = srrConfig.getIntermodalAccessEgressParameterSets();
        IntermodalAccessEgressParameterSet intermodalAccessEgressParameterSet = new IntermodalAccessEgressParameterSet();
        intermodalAccessEgressParameterSet.setMode("walk");
        intermodalAccessEgressParameterSets.add(intermodalAccessEgressParameterSet);

        PlanCalcScoreConfigGroup pcsConfig = config.planCalcScore();
        ModeParams modeParams = new ModeParams(TransportMode.non_network_walk);
        modeParams.setMarginalUtilityOfTraveling(1);
        pcsConfig.addModeParams(modeParams);

        this.scenario = ScenarioUtils.createScenario(config);
        new TransitScheduleReader(scenario).readFile(schedualFile);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(netwoekFile);

        RaptorStaticConfig raptorStaticConfig = new RaptorStaticConfig();
        raptorStaticConfig.setOptimization(RaptorOptimization.OneToAllRouting);
        SwissRailRaptorData data = SwissRailRaptorData.create(scenario.getTransitSchedule(), null, raptorStaticConfig, scenario.getNetwork(), null);
        this.data = data;

        RaptorIntermodalAccessEgress raptorIntermodalAccessEgress = new DefaultRaptorIntermodalAccessEgress();
        AccessEgressRouteCache accessEgressRouteCache = new AccessEgressRouteCache(null, new SingleModeNetworksCache(), config, scenario);
        SBBIntermodalRaptorStopFinder stopFinder = new SBBIntermodalRaptorStopFinder(config, raptorIntermodalAccessEgress, null, scenario.getTransitSchedule(), accessEgressRouteCache);
        this.stopFinder = stopFinder;

        RaptorParametersForPerson raptorParametersForPerson = new DefaultRaptorParametersForPerson(config);
        this.raptorParametersForPerson = raptorParametersForPerson;
        RaptorRouteSelector routeSelector = new LeastCostRaptorRouteSelector();
        RaptorInVehicleCostCalculator inVehicleCostCalculator = new DefaultRaptorInVehicleCostCalculator();
        RaptorTransferCostCalculator transferCostCalculator = new DefaultRaptorTransferCostCalculator();

        this.swissRailRaptor = new SwissRailRaptor(data, raptorParametersForPerson, routeSelector, stopFinder, inVehicleCostCalculator, transferCostCalculator);
        this.railTripsAnalyzer = new RailTripsAnalyzer(scenario.getTransitSchedule(), scenario.getNetwork());

        this.inputDemand = new InputDemand(columNames, demand, scenario);
    }

    public void routingWithBestPath() {
        inputDemand.getTimeList().stream().parallel().forEach(this::calculateMatrix);
        writeLinkCount();
    }

    private void calculateMatrix(Integer time) {
        long startTime = System.nanoTime();
        var raptor = new SwissRailRaptor(data, raptorParametersForPerson, routeSelector, stopFinder, inVehicleCostCalculator, transferCostCalculator);
        double[][] matrix = (double[][]) inputDemand.getOmxFile().getMatrix(time.toString()).getData();
        for (Entry<Integer, Coord> entryX : inputDemand.getValidPosistions().entrySet()) {
            for (Entry<Integer, Coord> entryY : inputDemand.getValidPosistions().entrySet()) {
                double timeDemand = matrix[entryX.getKey()][entryY.getKey()];
                if (timeDemand != 0) {
                    Facility startF = afFactory.createActivityFacility(Id.create(1, ActivityFacility.class), entryX.getValue());
                    Facility endF = afFactory.createActivityFacility(Id.create(2, ActivityFacility.class), entryY.getValue());
                    RoutingRequest request = DefaultRoutingRequest.withoutAttributes(startF, endF, (time - 1) * 600, null);
                    List<? extends PlanElement> legs = raptor.calcRoute(request);
                    if (legs == null) {
                        //System.out.println("No connection found for " + entryX.getValue() + " to " + entryX.getValue() + " at time " + time + " demand " + timeDemand);
                        //System.out.println("LINESTRING (" + entryX.getValue().getX() + " " + entryX.getValue().getY() + ", " + entryY.getValue().getX() + " " + entryY.getValue().getY() + ");" + time);
                        count++;
                        missingDemand += timeDemand;
                        continue;
                    }
                    routedDemand += timeDemand;
                    addDemand(timeDemand, legs);
                }
            }
        }
        System.out.println("Matrix: " + time + "; " + ((System.nanoTime() - startTime) / 1_000_000_000) + "s");
    }

    private void writeLinkCount() {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(output))) {
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

    private static Map<Id<Link>, DemandStorage> createLinkDemandStorage() {
        Map<Id<Link>, DemandStorage> idDemandStorageMap = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(saveFileInpout))) {
            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            while ((line = reader.readLine()) != null) {
                var linkId = Id.createLinkId(line.split(";")[header.indexOf("Matsim_Link")]);
                idDemandStorageMap.put(linkId, new DemandStorage(linkId, line.split(";")[header.indexOf("Visum_Link")], line.split(";")[header.indexOf("WKT")]));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return idDemandStorageMap;
    }

    void calculateTest() {
        {
            var raptor = new SwissRailRaptor(data, raptorParametersForPerson, routeSelector, stopFinder, inVehicleCostCalculator, transferCostCalculator);
            Facility startF = afFactory.createActivityFacility(Id.create(1, ActivityFacility.class),
                scenario.getTransitSchedule().getFacilities().get(Id.create(1311, TransitStopFacility.class)).getCoord());
            Map<Id<TransitStopFacility>, TravelInfo> tree = raptor.calcTree(startF, 1*3600, null, null);
            TravelInfo travelInfo = tree.get(Id.create(3289, TransitStopFacility.class));
            int pathlegindex = 1;
            int pathindex = getID();
            for (RoutePart routePart : travelInfo.getRaptorRoute().getParts()) {
                if (routePart.mode.equals("pt")) {
                    addToLine(routePart, pathindex, pathlegindex++);
                }
            }
            for (String s : lines) {
                System.out.println(s);
            }
            lines.clear();
        }
        System.out.println("----------------------------------------");
        var raptor = new SwissRailRaptor(data, raptorParametersForPerson, routeSelector, stopFinder, inVehicleCostCalculator, transferCostCalculator);
        Facility startF = afFactory.createActivityFacility(Id.create(4, ActivityFacility.class),
            scenario.getTransitSchedule().getFacilities().get(Id.create(1311, TransitStopFacility.class)).getCoord());
        Map<Id<TransitStopFacility>, TravelInfo> tree = raptor.calcTree(startF, 4*3600, null, null);
        TravelInfo travelInfo = tree.get(Id.create(3289, TransitStopFacility.class));
        int pathlegindex = 1;
        int pathindex = getID();
        for (RoutePart routePart : travelInfo.getRaptorRoute().getParts()) {
            if (routePart.mode.equals("pt")) {
                addToLine(routePart, pathindex, pathlegindex++);
            }
        }
        for (String s : lines) {
            System.out.println(s);
        }
    }

}
