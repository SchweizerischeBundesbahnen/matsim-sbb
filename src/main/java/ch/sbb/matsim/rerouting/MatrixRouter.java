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
import ch.sbb.matsim.routing.pt.raptor.RaptorRouteSelector;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig;
import ch.sbb.matsim.routing.pt.raptor.RaptorTransferCostCalculator;
import ch.sbb.matsim.routing.pt.raptor.SBBIntermodalRaptorStopFinder;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

public class MatrixRouter {

    final static String columNames = "Z:/99_Playgrounds/MD/Umlegung/Input/ColumNames.csv";
    final static String demand = "Z:/99_Playgrounds/MD/Umlegung/Input/NachfrageTag.omx";
    final static String saveFileInpout = "Z:/99_Playgrounds/MD/Umlegung/Input/saveFile.csv";
    final static String schedualFile = "Z:/99_Playgrounds/MD/Umlegung/Input/smallTransitSchedule.xml.gz";
    final static String netwoekFile = "Z:/99_Playgrounds/MD/Umlegung/Input//smallTransitNetwork.xml.gz";
    final static String output = "C:/devsbb/writeFilePlace/Umlegung/saveFileWithDemand.csv";

    final InputDemand inputDemand = new InputDemand(columNames, demand);
    final Map<Id<Link>, DemandStorage> idDemandStorageMap = createLinkDemandStorage();
    final ActivityFacilitiesFactory afFactory = ScenarioUtils.createScenario(ConfigUtils.createConfig()).getActivityFacilities().getFactory();
     final Scenario scenario;
    final SwissRailRaptor swissRailRaptor;
    final RailTripsAnalyzer railTripsAnalyzer;

    public static void main(String[] args) {
        long startTime = System.nanoTime();
        MatrixRouter matrixRouter = new MatrixRouter();
        matrixRouter.routingWithBestPath();
        System.out.println("It took " + ((System.nanoTime() - startTime)/1_000_000_000) + "s");
    }

    public MatrixRouter() {
        System.out.println("Config");
        Config config = ConfigUtils.createConfig();

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
        SwissRailRaptorData data = SwissRailRaptorData.create(scenario.getTransitSchedule(), null, raptorStaticConfig, scenario.getNetwork(), null);

        RaptorIntermodalAccessEgress raptorIntermodalAccessEgress = new DefaultRaptorIntermodalAccessEgress();
        AccessEgressRouteCache accessEgressRouteCache = new AccessEgressRouteCache(null, new SingleModeNetworksCache(), config, scenario);
        SBBIntermodalRaptorStopFinder stopFinder = new SBBIntermodalRaptorStopFinder(config, raptorIntermodalAccessEgress, null, scenario.getTransitSchedule(), accessEgressRouteCache);

        RaptorParametersForPerson raptorParametersForPerson = new DefaultRaptorParametersForPerson(config);
        RaptorRouteSelector routeSelector = new LeastCostRaptorRouteSelector();
        RaptorInVehicleCostCalculator inVehicleCostCalculator = new DefaultRaptorInVehicleCostCalculator();
        RaptorTransferCostCalculator transferCostCalculator = new DefaultRaptorTransferCostCalculator();

        this.swissRailRaptor = new SwissRailRaptor(data, raptorParametersForPerson, routeSelector, stopFinder, inVehicleCostCalculator, transferCostCalculator);
        this.railTripsAnalyzer = new RailTripsAnalyzer(scenario.getTransitSchedule(), scenario.getNetwork());
    }

    public void routingWithBestPath() {
       inputDemand.getTimeList().stream().parallel().forEach(this::calculateMatrix);
       writeLinkCount();
    }

    private void calculateMatrix(Integer time) {
        System.out.println(Thread.currentThread().getName());
        double[][] matrix = (double[][]) inputDemand.getOmxFile().getMatrix(time.toString()).getData();
        for (int x = 0; x < inputDemand.getLastPosition(); x++) {
            if (scenario.getTransitSchedule().getFacilities().get(Id.create(inputDemand.getCodeList().get(x), TransitStopFacility.class)) == null) {
                continue;
            }
            for (int y = 0; y < inputDemand.getLastPosition(); y++) {
                if (scenario.getTransitSchedule().getFacilities().get(Id.create(inputDemand.getCodeList().get(y), TransitStopFacility.class)) == null) {
                    continue;
                }
                double timeDemand = matrix[x][y];
                if (timeDemand != 0) {
                    Coord startCoord = scenario.getTransitSchedule().getFacilities().get(Id.create(inputDemand.getCodeList().get(x), TransitStopFacility.class)).getCoord();
                    Coord endCoord = scenario.getTransitSchedule().getFacilities().get(Id.create(inputDemand.getCodeList().get(y), TransitStopFacility.class)).getCoord();
                    Facility startF = afFactory.createActivityFacility(Id.create(1, ActivityFacility.class), startCoord);
                    Facility endF = afFactory.createActivityFacility(Id.create(2, ActivityFacility.class), endCoord);
                    RoutingRequest request = DefaultRoutingRequest.withoutAttributes(startF, endF, inputDemand.getTime() * 600, null);
                    List<? extends PlanElement> legs = swissRailRaptor.calcRoute(request);
                    if (legs == null) {
                        //System.out.println("No connection found for " + inputDemand.getCodeList().get(inputDemand.getXPosition()) + " to " + inputDemand.getCodeList().get(inputDemand.getYPosition()));
                        continue;
                    }
                    for (PlanElement pe : legs) {
                        Leg leg = (Leg) pe;
                        if (leg.getMode().equals("pt")) {
                            List<Id<Link>> linkIds = railTripsAnalyzer.getPtLinkIdsTraveledOn((TransitPassengerRoute) leg.getRoute());
                            for (Id<Link> linkId : linkIds) {
                                if (scenario.getNetwork().getLinks().get(linkId).getFromNode().equals(scenario.getNetwork().getLinks().get(linkId).getToNode())) {
                                    continue;
                                }
                                idDemandStorageMap.get(linkId).increaseDemand(timeDemand);
                            }
                        }
                    }
                }
            }
        }
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
}
