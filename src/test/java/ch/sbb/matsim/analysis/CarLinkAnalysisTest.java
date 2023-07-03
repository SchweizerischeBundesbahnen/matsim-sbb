package ch.sbb.matsim.analysis;

import ch.sbb.matsim.analysis.linkAnalysis.CarLinkAnalysis;
import ch.sbb.matsim.analysis.linkAnalysis.IterationLinkAnalyzer;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import org.junit.Test;
import org.locationtech.jts.util.Assert;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.LinkEnterEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.vehicles.Vehicle;

/**
 * @author mrieser
 */
public class CarLinkAnalysisTest {

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Test
    public void testLinkVolumeTracking() throws IOException {
        Fixture f = new Fixture();
        IterationLinkAnalyzer iterationLinkAnalyzer = new IterationLinkAnalyzer(f.scenario);
        f.events.addHandler(iterationLinkAnalyzer);
        CarLinkAnalysis carLinkAnalysis = new CarLinkAnalysis(ConfigUtils.addOrGetModule(f.config, PostProcessingConfigGroup.class), f.scenario, iterationLinkAnalyzer);

        Id<Person> personId = Id.create(1, Person.class);
        Id<Vehicle> vehicleId = Id.create(2, Vehicle.class);
        Id<Link> linkHome = Id.create("L_1", Link.class);
        Id<Link> linkWork = Id.create("B_2", Link.class);
        Id<Link> linkShop = Id.create("T_3", Link.class);

        f.events.processEvent(new VehicleEntersTrafficEvent(7.00 * 3600, personId, linkHome, vehicleId, "car", 1.0));

        f.events.processEvent(new LinkEnterEvent(7.25 * 3600, vehicleId, linkWork));
        f.events.processEvent(new VehicleLeavesTrafficEvent(7.25 * 3600, personId, linkWork, vehicleId, "car", 1.0));
        f.events.processEvent(new ActivityStartEvent(7.25 * 3600, personId, linkWork, null, "work", null));

        f.events.processEvent(new VehicleEntersTrafficEvent(12.00 * 3600, personId, linkWork, vehicleId, "car", 1.0));

        f.events.processEvent(new VehicleLeavesTrafficEvent(12.25 * 3600, personId, linkShop, vehicleId, "car", 1.0));
        f.events.processEvent(new ActivityStartEvent(12.25 * 3600, personId, linkShop, null, "shop", null));

        f.events.processEvent(new VehicleEntersTrafficEvent(13.00 * 3600, personId, linkShop, vehicleId, "car", 1.0));

        f.events.processEvent(new VehicleLeavesTrafficEvent(13.25 * 3600, personId, linkHome, vehicleId, "car", 1.0));
        f.events.processEvent(new ActivityStartEvent(13.25 * 3600, personId, linkHome, null, "home", null));

        f.events.processEvent(new VehicleEntersTrafficEvent(15.00 * 3600, personId, linkShop, vehicleId, "car", 1.0));

        f.events.processEvent(new VehicleLeavesTrafficEvent(15.25 * 3600, personId, linkWork, vehicleId, "car", 1.0));
        f.events.processEvent(new ActivityStartEvent(13.25 * 3600, personId, linkWork, null, "shop", null));

        f.events.processEvent(new VehicleEntersTrafficEvent(18.00 * 3600, personId, linkWork, vehicleId, "car", 1.0));

        f.events.processEvent(new VehicleLeavesTrafficEvent(18.50 * 3600, personId, linkHome, vehicleId, "car", 1.0));
        f.events.processEvent(new ActivityStartEvent(18.00 * 3600, personId, linkHome, null, "shop", null));

        File outputDir = new File("test/output/ch/sbb/matsim/analysis/linkAnalyser");
        outputDir.mkdirs();
        carLinkAnalysis.writeSingleIterationStreetStats(outputDir.getAbsolutePath() + "/test.att");
        File f1 = new File(outputDir.getAbsolutePath() + "/test.att");
        File f2 = new File("test/input/ch/sbb/matsim/analysis/linkAnalyser/carlinkanalysistestvolumes.att");
        Assert.equals(true, Files.readLines(f1, Charsets.UTF_8).equals(Files.readLines(f2, Charsets.UTF_8)));
    }

    /**
     * Creates a simple test scenario matching the accesstime_zone.SHP test file.
     */
    private static class Fixture {

        final Config config;
        final Scenario scenario;
        EventsManager events;

        public Fixture() {
            this.config = ConfigUtils.createConfig();
            prepareConfig();
            this.scenario = ScenarioUtils.createScenario(this.config);
            prePopulation();
            createNetwork();
            prepareEvents();
        }

        private void prepareConfig() {
            PostProcessingConfigGroup postProcessingConfigGroup = new PostProcessingConfigGroup();
            postProcessingConfigGroup.setSimulationSampleSize(1.0);
            config.addModule(postProcessingConfigGroup);
        }

        private void prePopulation() {
            Population population = scenario.getPopulation();
            PopulationFactory populationFactory = population.getFactory();
            Person person = populationFactory.createPerson(Id.createPersonId(1));
            population.addPerson(person);
            person.getAttributes().putAttribute("subpopulation", "regular");
            Plan plan = populationFactory.createPlan();
            person.addPlan(plan);
            person.setSelectedPlan(plan);

            Activity activity1 = populationFactory.createActivityFromActivityFacilityId("home", null);
            Leg leg = populationFactory.createLeg("ride");
            Activity activity2 = populationFactory.createActivityFromActivityFacilityId("home", null);
            Route route = populationFactory.getRouteFactories().createRoute(null, null,null);
            route.setRouteDescription("L_1 B_2 T_3");
            leg.setRoute(route);
            plan.addActivity(activity1);
            plan.addLeg(leg);
            plan.addActivity(activity2);
        }

        private void createNetwork() {
            Network network = this.scenario.getNetwork();
            NetworkFactory nf = network.getFactory();

            Node nL1 = nf.createNode(Id.create("L1", Node.class), new Coord(545000, 150000));
            Node nL2 = nf.createNode(Id.create("L2", Node.class), new Coord(540000, 165000));
            Node nB1 = nf.createNode(Id.create("B1", Node.class), new Coord(595000, 205000));
            Node nB2 = nf.createNode(Id.create("B2", Node.class), new Coord(605000, 195000));
            Node nT1 = nf.createNode(Id.create("T1", Node.class), new Coord(610000, 180000));
            Node nT2 = nf.createNode(Id.create("T2", Node.class), new Coord(620000, 175000));

            network.addNode(nL1);
            network.addNode(nL2);
            network.addNode(nB1);
            network.addNode(nB2);
            network.addNode(nT1);
            network.addNode(nT2);

            Link lL = createLink(nf, "L_1", nL1, nL2, 500, 1000, 10);
            Link lLB = createLink(nf, "LB_12", nL2, nB1, 5000, 2000, 25);
            Link lB = createLink(nf, "B_2", nB1, nB2, 500, 1000, 10);
            Link lBT = createLink(nf, "BT_23", nB2, nT1, 5000, 2000, 25);
            Link lT = createLink(nf, "T_3", nT1, nT2, 500, 1000, 10);
            Link lTL = createLink(nf, "TL_31", nT2, nL1, 5000, 2000, 25);

            network.addLink(lL);
            network.addLink(lLB);
            network.addLink(lB);
            network.addLink(lBT);
            network.addLink(lT);
            network.addLink(lTL);
        }

        private Link createLink(NetworkFactory nf, String id, Node fromNode, Node toNode, double length, double capacity, double freespeed) {
            Link l = nf.createLink(Id.create(id, Link.class), fromNode, toNode);
            l.setLength(length);
            l.setCapacity(capacity);
            l.setFreespeed(freespeed);
            l.setAllowedModes(CollectionUtils.stringToSet("car"));
            l.setNumberOfLanes(1);
            return l;
        }

        private void prepareEvents() {
            this.events = EventsUtils.createEventsManager(this.config);
        }

    }
}