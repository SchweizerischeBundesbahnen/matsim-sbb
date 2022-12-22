package ch.sbb.matsim.rerouting;

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
import ch.sbb.matsim.routing.pt.raptor.RaptorRouteSelector;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig;
import ch.sbb.matsim.routing.pt.raptor.RaptorTransferCostCalculator;
import ch.sbb.matsim.routing.pt.raptor.SBBIntermodalRaptorStopFinder;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesLoader;
import java.util.List;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
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
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;

public class RoutingWithBestPath {

    static String demandFiles = "";
    static String zoneFile = "Z:/99_Playgrounds/MD/Umlegung/smallInput/mobi-zones_2056.shp";
    static String schedualFile = "Z:/99_Playgrounds/MD/Umlegung/smallInput/smallTransitSchedule.xml.gz";
    static String netwoekFile = "Z:/99_Playgrounds/MD/Umlegung/smallInput//smallTransitNetwork.xml.gz";

    public static void main(String[] args) {

        Config config = ConfigUtils.createConfig();

        PostProcessingConfigGroup ppConfig = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class);
        ppConfig.setZonesId("zones");
        Zones zones = ZonesLoader.loadZones("zones", zoneFile, "ID");
        ZonesCollection zonesCollection = new ZonesCollection();
        zonesCollection.addZones(zones);

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
        AccessEgressRouteCache accessEgressRouteCache = new AccessEgressRouteCache(zonesCollection, new SingleModeNetworksCache(),config, scenario);
        SBBIntermodalRaptorStopFinder stopFinder = new SBBIntermodalRaptorStopFinder(config, raptorIntermodalAccessEgress, null, scenario.getTransitSchedule(), accessEgressRouteCache);


        RaptorParametersForPerson raptorParametersForPerson = new DefaultRaptorParametersForPerson(config);
        RaptorRouteSelector routeSelector = new LeastCostRaptorRouteSelector();
        RaptorInVehicleCostCalculator inVehicleCostCalculator = new DefaultRaptorInVehicleCostCalculator();
        RaptorTransferCostCalculator transferCostCalculator = new DefaultRaptorTransferCostCalculator();
        SwissRailRaptor swissRailRaptor = new SwissRailRaptor(data, raptorParametersForPerson, routeSelector, stopFinder, inVehicleCostCalculator, transferCostCalculator);

        //SwissRailRaptorFactory swissRailRaptorFactory = new SwissRailRaptorFactory(scenario, config, raptorParametersForPerson, routeSelector, stopFinderProvider);

        ActivityFacilitiesFactory afFactory = new ActivityFacilitiesFactoryImpl();
        Facility startF = afFactory.createActivityFacility(Id.create(1, ActivityFacility.class), new Coord(2599961.29864705, 1199752.15428274));
        Facility endF = afFactory.createActivityFacility(Id.create(2, ActivityFacility.class), new Coord(2683158.50435868, 1248057.07470594));

        PopulationFactory pFactory = scenario.getPopulation().getFactory();
        Person person = pFactory.createPerson(Id.createPersonId("person"));

        RoutingRequest request = DefaultRoutingRequest.withoutAttributes(startF, endF, 6 * 3600 , person);

        List<? extends PlanElement> route = swissRailRaptor.calcRoute(request);

        System.out.println("Done");

    }



}
