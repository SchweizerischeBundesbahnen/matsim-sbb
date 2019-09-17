package ch.sbb.matsim.calibration;


import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.config.SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.intermodal.SBBRaptorIntermodalAccessEgress;
import ch.sbb.matsim.preparation.FilteredNetwork;
import ch.sbb.matsim.routing.network.SBBNetworkRoutingInclAccessEgressModule;
import ch.sbb.matsim.routing.pt.raptor.*;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesLoader;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.TeleportationRoutingModule;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.Facility;
import org.matsim.pt.router.TransitRouter;
import org.matsim.utils.objectattributes.ObjectAttributes;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.stream.IntStream;

public class RideFeederCalibration {


    public static void main(String[] args) throws IOException {
        System.setProperty("matsim.preferLocalDtds", "true");

        String config_path = args[0];
        String csv_path = args[1];
        String output_csv_path = args[2];

        RideFeederCalibration rideFeederCalibration = new RideFeederCalibration();
        rideFeederCalibration.init(config_path);
        rideFeederCalibration.computeRelations(csv_path, output_csv_path);

    }

    private Scenario scenario;
    private Network filteredNetwork;
    private Person person1;
    private Config config;
    private SwissRailRaptorData data;
    private Zones zones;

    public RideFeederCalibration() {

    }

    private void init(String config_path) {

        Config config = RunSBB.buildConfig(config_path);
        config.plans().setInputFile(null);
        config.plans().setInputPersonAttributeFile(null);
        config.facilities().setInputFile(null);

        ZonesCollection zonesCollection = new ZonesCollection();
        ZonesLoader.loadAllZones(config, zonesCollection);
        this.zones = zonesCollection.getZones(Id.create("npvm_zones", Zones.class));

        this.config = config;
        this.scenario = ScenarioUtils.loadScenario(config);

        for (Link link : scenario.getNetwork().getLinks().values()) {
            if (link.getAllowedModes().contains(TransportMode.car)) {
                Set<String> allowedModes = new HashSet<>();
                for (String mode : link.getAllowedModes())
                    allowedModes.add(mode);
                allowedModes.add("ride_feeder");
                link.setAllowedModes(allowedModes);
            }
        }

        this.filteredNetwork = new FilteredNetwork().filterNetwork(this.scenario.getNetwork());


        this.person1 = this.scenario.getPopulation().getFactory().createPerson(Id.create("1", Person.class));
        ObjectAttributes personAttributes = this.scenario.getPopulation().getPersonAttributes();
        personAttributes.putAttribute(person1.getId().toString(), "subpopulation", "regular");
        this.scenario.getPopulation().addPerson(person1);

        this.initRaptorData();
    }

    public void computeRelations(String csv_path, String output_csv_path) {

        try (CSVWriter csvWriter = new CSVWriter("", new String[]{"trip_id", "leg_id", "mode", "distance", "duration", "constant", "mutt", "waiting"}, output_csv_path)) {


            try (CSVReader visumVolume = new CSVReader(new String[]{"name", "trip_id", "from_x", "from_y", "to_x", "to_y", "time"}, csv_path, ";")) {
                visumVolume.readLine();

                Map<String, String> row;

                while ((row = visumVolume.readLine()) != null) {
                    System.out.println(row.get("name"));
                    Coord fromCoord = new Coord(Float.parseFloat(row.get("from_x")), Float.parseFloat(row.get("from_y")));
                    Coord toCoord = new Coord(Float.parseFloat(row.get("to_x")), Float.parseFloat(row.get("to_y")));

                    Map<String, String> row2 = row;

                    Facility fromFacility = this.scenario.getActivityFacilities().getFactory().createActivityFacility(Id.create(1, ActivityFacility.class), fromCoord, NetworkUtils.getNearestLink(filteredNetwork, fromCoord).getId());
                    Facility toFacility = this.scenario.getActivityFacilities().getFactory().createActivityFacility(Id.create(2, ActivityFacility.class), toCoord, NetworkUtils.getNearestLink(filteredNetwork, toCoord).getId());

                    int nWaiting = 10;
                    int nConstant = 10; // 10;
                    int nMutt = 10;

                    IntStream.range(0, nWaiting).forEachOrdered(waiting_i -> {
                        IntStream.range(0, nConstant).forEachOrdered(constant_i -> {

                            IntStream.range(0, nMutt).forEachOrdered(mutt_i -> {


                                double waiting = waiting_i / ((double) nWaiting) * 30 * 60;
                                double constant = constant_i / ((double) nConstant) * 2.0;
                                double mutt = mutt_i / ((double) nMutt) * 0.005;


                                List<SBBIntermodalModeParameterSet> modeParams = Collections.singletonList(new SBBIntermodalModeParameterSet("ride_feeder", (int) waiting, constant, mutt, 1.3));
                                TransitRouter router = this.createTransitRouter(new SBBRaptorIntermodalAccessEgress(modeParams));
                                List<Leg> legs = router.calcRoute(fromFacility, toFacility, Integer.parseInt(row2.get("time")), person1);
                                int leg_id = 1;
                                for (Leg leg : legs) {
                                    csvWriter.set("trip_id", row2.get("trip_id"));
                                    csvWriter.set("leg_id", String.valueOf(leg_id));
                                    csvWriter.set("mode", leg.getMode());
                                    csvWriter.set("distance", String.valueOf(leg.getRoute().getDistance()));
                                    csvWriter.set("duration", String.valueOf(leg.getTravelTime()));
                                    csvWriter.set("constant", String.valueOf(constant));
                                    csvWriter.set("mutt", String.valueOf(mutt));
                                    csvWriter.set("waiting", String.valueOf(waiting));
                                    leg_id++;
                                    csvWriter.writeRow();
                                }


                            });
                        });
                    });


                }
            } catch (IOException e2) {
                throw new UncheckedIOException(e2);
            }


        } catch (IOException e) {


        }


    }


    private void initRaptorData() {


        RaptorStaticConfig swissRailRaptorConfig = RaptorUtils.createStaticConfig(config);


        SwissRailRaptorData data = SwissRailRaptorData.create(scenario.getTransitSchedule(), swissRailRaptorConfig, scenario.getNetwork());
        this.data = data;

    }

    private SwissRailRaptor createTransitRouter(RaptorIntermodalAccessEgress intermodalAE) {

        DijkstraFactory factory = new DijkstraFactory();
        TravelTime tt = new FreeSpeedTravelTime();
        TravelDisutility td = new OnlyTimeDependentTravelDisutility(tt);

        LeastCostPathCalculator routeAlgo = factory.createPathCalculator(scenario.getNetwork(), td, tt);

        Map<String, RoutingModule> routingModules = new HashMap<>();


        PlansCalcRouteConfigGroup plansCalcRouteConfigGroup = scenario.getConfig().plansCalcRoute();
        plansCalcRouteConfigGroup.setInsertingAccessEgressWalk(true);

        routingModules.put("ride_feeder", new SBBNetworkRoutingInclAccessEgressModule("car", scenario.getPopulation().getFactory(), scenario.getNetwork(),
                routeAlgo, plansCalcRouteConfigGroup, zones));
        routingModules.put(TransportMode.walk,
                new TeleportationRoutingModule(TransportMode.walk, scenario.getPopulation().getFactory(), 1.1, 1.3));


        DefaultRaptorStopFinder stopFinder = new DefaultRaptorStopFinder(scenario.getPopulation(), intermodalAE, routingModules);
        return new SwissRailRaptor(this.data, new DefaultRaptorParametersForPerson(this.config),
                new LeastCostRaptorRouteSelector(),
                stopFinder, "subpopulation", scenario.getPopulation().getPersonAttributes());
    }


}
