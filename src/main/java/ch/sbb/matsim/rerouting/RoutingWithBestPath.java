package ch.sbb.matsim.rerouting;

import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailTripsAnalyzer;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
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
import ch.sbb.matsim.routing.pt.raptor.RaptorRoute;
import ch.sbb.matsim.routing.pt.raptor.RaptorRouteSelector;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig;
import ch.sbb.matsim.routing.pt.raptor.RaptorTransferCostCalculator;
import ch.sbb.matsim.routing.pt.raptor.RaptorUtils;
import ch.sbb.matsim.routing.pt.raptor.SBBIntermodalRaptorStopFinder;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesLoader;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.PopulationFactory;
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
import org.matsim.facilities.ActivityFacilitiesFactoryImpl;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.Facility;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class RoutingWithBestPath {

    static String demandFilesLocation = "Z:/99_Playgrounds/MD/Umlegung/Nachfrage/";
    static String demand = "Z:/99_Playgrounds/MD/Umlegung/smallInput/NachfrageTag.omx";
    static String saveFileInpout = "Z:/99_Playgrounds/MD/Umlegung/saveFile.csv";
    static String zoneFile = "Z:/99_Playgrounds/MD/Umlegung/smallInput/mobi-zones_2056.shp";
    static String schedualFile = "Z:/99_Playgrounds/MD/Umlegung/smallInput/smallTransitSchedule.xml.gz";
    static String netwoekFile = "Z:/99_Playgrounds/MD/Umlegung/smallInput//smallTransitNetwork.xml.gz";
    static String output = "C:/devsbb/writeFilePlace/Umlegung/saveFileWithDemand.csv";

    public static void main(String[] args) {

        /*if (args.length != 0) {
            demandFilesLocation = args[0];
            saveFileInpout = args[1];
            zoneFile = args[2];
            schedualFile = args[3];
            netwoekFile = args[4];
            output = args[5];
        }*/

        Config config = ConfigUtils.createConfig();

        /*
        PostProcessingConfigGroup ppConfig = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class);
        ppConfig.setZonesId("zones");
        Zones zones = ZonesLoader.loadZones("zones", zoneFile, "ID");
        ZonesCollection zonesCollection = new ZonesCollection();
        zonesCollection.addZones(zones);
        */

        SwissRailRaptorConfigGroup srrConfig = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class);
        List<IntermodalAccessEgressParameterSet> intermodalAccessEgressParameterSets = srrConfig.getIntermodalAccessEgressParameterSets();
        IntermodalAccessEgressParameterSet intermodalAccessEgressParameterSet = new IntermodalAccessEgressParameterSet();
        intermodalAccessEgressParameterSet.setMode("walk");
        intermodalAccessEgressParameterSets.add(intermodalAccessEgressParameterSet);

        PlanCalcScoreConfigGroup pcsConfig = config.planCalcScore();
        ModeParams modeParams = new ModeParams(TransportMode.non_network_walk);
        modeParams.setMarginalUtilityOfTraveling(1);
        pcsConfig.addModeParams(modeParams);

        Scenario scenario = ScenarioUtils.createScenario(config);
        new TransitScheduleReader(scenario).readFile(schedualFile);
        new MatsimNetworkReader(scenario.getNetwork()).readFile(netwoekFile);

        RaptorStaticConfig raptorStaticConfig = new RaptorStaticConfig();
        SwissRailRaptorData data = SwissRailRaptorData.create(scenario.getTransitSchedule(), null, raptorStaticConfig, scenario.getNetwork(), null);

        RaptorIntermodalAccessEgress raptorIntermodalAccessEgress = new DefaultRaptorIntermodalAccessEgress();
        AccessEgressRouteCache accessEgressRouteCache = new AccessEgressRouteCache(null, new SingleModeNetworksCache(),config, scenario);
        SBBIntermodalRaptorStopFinder stopFinder = new SBBIntermodalRaptorStopFinder(config, raptorIntermodalAccessEgress, null, scenario.getTransitSchedule(), accessEgressRouteCache);

        RaptorParametersForPerson raptorParametersForPerson = new DefaultRaptorParametersForPerson(config);
        RaptorRouteSelector routeSelector = new LeastCostRaptorRouteSelector();
        RaptorInVehicleCostCalculator inVehicleCostCalculator = new DefaultRaptorInVehicleCostCalculator();
        RaptorTransferCostCalculator transferCostCalculator = new DefaultRaptorTransferCostCalculator();
        SwissRailRaptor swissRailRaptor = new SwissRailRaptor(data, raptorParametersForPerson, routeSelector, stopFinder, inVehicleCostCalculator, transferCostCalculator);

        //SwissRailRaptorFactory swissRailRaptorFactory = new SwissRailRaptorFactory(scenario, config, raptorParametersForPerson, routeSelector, stopFinderProvider);
        Map<Id<Link>, DemandStorage> idDemandStorageMap = createDemandStorage(saveFileInpout);
        List<String> demandFiles = findAllDemandFiles();
        RailTripsAnalyzer railTripsAnalyzer = new RailTripsAnalyzer(scenario.getTransitSchedule(), scenario.getNetwork());
        PopulationFactory pFactory = scenario.getPopulation().getFactory();
        ActivityFacilitiesFactory afFactory = new ActivityFacilitiesFactoryImpl();
        Person person = pFactory.createPerson(Id.createPersonId("person"));

        ReadOMXMatrciesDayDemand.readOMXMatrciesDayDemand(demand);

        long startTime = System.nanoTime();
        for (String demandSegment : demandFiles) {
            try (BufferedReader reader = new BufferedReader(new FileReader(demandSegment))) {
                String fileName = demandSegment.split("/")[demandSegment.split("/").length-1];
                String endFile = fileName.split("_")[2];
                int time = Integer.parseInt(endFile.split("\\.")[0]);
                System.out.println(time);
                List<String> header = List.of(reader.readLine().split(","));
                String line;
                while ((line = reader.readLine()) != null) {
                    var startLocationIds = line.split(",");
                    for (String endLocationId : header) {
                        if (endLocationId.equals("Nachfrage")) {
                            continue;
                        }
                        String stringDemand = startLocationIds[header.indexOf(endLocationId)];
                        if (stringDemand.equals("-")) {
                            continue;
                        }
                        var startlocationId = startLocationIds[header.indexOf("Nachfrage")];
                        Coord startCoord = scenario.getTransitSchedule().getFacilities().get(Id.create(startlocationId, TransitStopFacility.class)).getCoord();
                        Coord endCoord = scenario.getTransitSchedule().getFacilities().get(Id.create(endLocationId, TransitStopFacility.class)).getCoord();
                        Facility startF = afFactory.createActivityFacility(Id.create(1, ActivityFacility.class), startCoord);
                        Facility endF = afFactory.createActivityFacility(Id.create(2, ActivityFacility.class), endCoord);
                        RoutingRequest request = DefaultRoutingRequest.withoutAttributes(startF, endF, time * 60, person);
                        List<? extends PlanElement> legs = swissRailRaptor.calcRoute(request);
                        List<RaptorRoute> routes = swissRailRaptor.calcRoutes(startF, endF, time*60, time*60, time*60+5000, person, null);
                        List<List<? extends PlanElement>> routesLegs = new ArrayList<>();
                        int x = 1;
                        for (RaptorRoute route : routes) {
                            idDemandStorageMap = createDemandStorage(saveFileInpout);
                            legs = RaptorUtils.convertRouteToLegs(route, raptorStaticConfig.getTransferWalkMargin());
                            routesLegs.add(legs);
                            for (PlanElement pe : legs) {
                                Leg leg = (Leg) pe;
                                if (leg.getMode().equals("pt")) {
                                    List<Id<Link>> linkIds = railTripsAnalyzer.getPtLinkIdsTraveledOn((TransitPassengerRoute) leg.getRoute());
                                    for (Id<Link> linkId : linkIds) {
                                        if (scenario.getNetwork().getLinks().get(linkId).getFromNode().equals(scenario.getNetwork().getLinks().get(linkId).getToNode())) {
                                            continue;
                                        }
                                        DemandStorage demandStorage = idDemandStorageMap.get(linkId);
                                        demandStorage.demand++;
                                    }
                                }
                            }
                            try (BufferedWriter writer = new BufferedWriter(new FileWriter("C:/devsbb/writeFilePlace/Umlegung/saveFileWithDemand" + x + ".csv"))) {
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
                            x++;
                        }
                        if (legs == null) {
                            continue;
                        }
                        int demand = Integer.parseInt(startLocationIds[header.indexOf(endLocationId)]);
                        break;
                    }
                    break;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            break;
        }
        System.out.println("Done");
        String outputSaveFile = output;
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
        System.out.println("It took " + (System.nanoTime() - startTime) + "s");
    }

    private static Map<Id<Link>, DemandStorage> createDemandStorage(String saveFileInput) {
        Map<Id<Link>, DemandStorage> idDemandStorageMap = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(saveFileInput))) {
            List<String> header = List.of(reader.readLine().split(";"));
            String line;
            while ((line = reader.readLine()) != null) {
                var linkId = Id.createLinkId(line.split(";")[header.indexOf("Matsim_Link")]);
                idDemandStorageMap.put(linkId, new DemandStorage(linkId, line.split(";")[header.indexOf("Visum_Link")],line.split(";")[header.indexOf("WKT")]));

            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return idDemandStorageMap;
    }

    private static List<String> findAllDemandFiles() {
        List<String> demandFiles = new ArrayList<>();
        for (int i = 10; i <= 1440; i += 10) {
            demandFiles.add(demandFilesLocation + "Test_Nachfrage_" + i + ".csv");
        }
        return demandFiles;
    }

}
