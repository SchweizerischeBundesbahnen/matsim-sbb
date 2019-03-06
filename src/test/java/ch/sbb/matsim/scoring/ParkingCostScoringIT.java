package ch.sbb.matsim.scoring;

import ch.sbb.matsim.config.ParkingCostConfigGroup;
import ch.sbb.matsim.config.SBBBehaviorGroupsConfigGroup;
import ch.sbb.matsim.config.ZonesListConfigGroup;
import ch.sbb.matsim.events.ParkingCostEvent;
import ch.sbb.matsim.vehicles.ParkingCostVehicleTracker;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesLoader;
import ch.sbb.matsim.zones.ZonesModule;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.handler.EventHandler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.testcases.MatsimTestUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author mrieser
 */
public class ParkingCostScoringIT {

    private static final Logger log = Logger.getLogger(ParkingCostScoringIT.class);

    @Rule
    public MatsimTestUtils helper = new MatsimTestUtils();

    @Test
    public void testParkingCostScoring() {
        Fixture f = new Fixture();

        f.config.controler().setLastIteration(0);

        double scoreWithout;
        double scoreWith;

        ParkingCostEventCollector parkingCostCollectorWithout = new ParkingCostEventCollector();
        ParkingCostEventCollector parkingCostCollectorWith = new ParkingCostEventCollector();

        { // run 1 without parking cost
            f.config.controler().setOutputDirectory(this.helper.getOutputDirectory() + "/without");
            Controler controler = new Controler(f.scenario);
            ScoringFunctionFactory scoringFunctionFactory = new SBBScoringFunctionFactory(f.scenario);
            controler.setScoringFunctionFactory(scoringFunctionFactory);

            controler.addOverridingModule(new AbstractModule() {
                @Override
                public void install() {
                    addEventHandlerBinding().toInstance(parkingCostCollectorWithout);
                }
            });

            controler.run();

            Person person = f.scenario.getPopulation().getPersons().get(Id.create("1", Person.class));
            scoreWithout = person.getSelectedPlan().getScore();
        }

        { // run 2 with parking cost
            f.config.controler().setOutputDirectory(this.helper.getOutputDirectory() + "/with");
            Controler controler = new Controler(f.scenario);
            ScoringFunctionFactory scoringFunctionFactory = new SBBScoringFunctionFactory(f.scenario);
            controler.setScoringFunctionFactory(scoringFunctionFactory);

            controler.addOverridingModule(new AbstractModule() {
                @Override
                public void install() {
                    install(new ZonesModule());
                    addEventHandlerBinding().to(ParkingCostVehicleTracker.class);
                    addEventHandlerBinding().toInstance(parkingCostCollectorWith);
                }
            });

            controler.run();
            scoreWith = f.scenario.getPopulation().getPersons().get(Id.create("1", Person.class)).getSelectedPlan().getScore();
        }

        log.info("Agent's score without parking cost: " + scoreWithout);
        log.info("Agent's score with parking cost: " + scoreWith);

        Assert.assertEquals("There should be no parking-cost event in the first case.", 0, parkingCostCollectorWithout.events.size());
        Assert.assertEquals("There should be some parking-cost event in the second case.", 3, parkingCostCollectorWith.events.size());
        Assert.assertTrue("Agent's score with parking cost should be lower than the score without parking costs.", scoreWith < scoreWithout);

    }

    public interface ParkingCostEventHandler extends EventHandler {
        @SuppressWarnings("unused")
        void handleEvent(ParkingCostEvent event);
    }

    private static class ParkingCostEventCollector implements ParkingCostEventHandler {
        final List<ParkingCostEvent> events = new ArrayList<>();

        @Override
        public void handleEvent(ParkingCostEvent event) {
            this.events.add(event);
        }
    }

    /**
     * Creates a simple test scenario matching the accesstime_zone.SHP test file.
     */
    private static class Fixture {
        Config config;
        Scenario scenario;
        ZonesCollection zones = new ZonesCollection();
        EventsManager events;

        Fixture() {
            this.config = ConfigUtils.createConfig();
            prepareConfig();
            this.scenario = ScenarioUtils.createScenario(this.config);
            createNetwork();
            createPopulation();
            loadZones();
            prepareEvents();
        }

        private void prepareConfig() {
            ZonesListConfigGroup zonesConfig = ConfigUtils.addOrGetModule(this.config, ZonesListConfigGroup.class);
            ZonesListConfigGroup.ZonesParameterSet parkingZonesConfig = new ZonesListConfigGroup.ZonesParameterSet();
            parkingZonesConfig.setFilename("src/test/resources/shapefiles/AccessTime/accesstime_zone.SHP");
            parkingZonesConfig.setId("parkingZones");
            parkingZonesConfig.setIdAttributeName("ID");
            zonesConfig.addZones(parkingZonesConfig);

            ParkingCostConfigGroup parkingConfig = ConfigUtils.addOrGetModule(this.config, ParkingCostConfigGroup.class);
            parkingConfig.setZonesId("parkingZones");
            parkingConfig.setZonesParkingCostAttributeName("ACCCAR"); // yes, we misuse the access times in the test data as parking costs

            SBBBehaviorGroupsConfigGroup sbbScoringConfig = ConfigUtils.addOrGetModule(this.config, SBBBehaviorGroupsConfigGroup.class);
            sbbScoringConfig.setMarginalUtilityOfParkingPrice(-0.1);

            PlanCalcScoreConfigGroup scoringConfig = this.config.planCalcScore();
            PlanCalcScoreConfigGroup.ActivityParams homeScoring = new PlanCalcScoreConfigGroup.ActivityParams("home");
            homeScoring.setTypicalDuration(12*3600);
            scoringConfig.addActivityParams(homeScoring);
            PlanCalcScoreConfigGroup.ActivityParams workScoring = new PlanCalcScoreConfigGroup.ActivityParams("work");
            workScoring.setTypicalDuration(8*3600);
            scoringConfig.addActivityParams(workScoring);
            PlanCalcScoreConfigGroup.ActivityParams shopScoring = new PlanCalcScoreConfigGroup.ActivityParams("shop");
            shopScoring.setTypicalDuration(8*3600);
            scoringConfig.addActivityParams(shopScoring);

            this.config.controler().setCreateGraphs(false);
            this.config.controler().setDumpDataAtEnd(false);
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

            Link lL = createLink(nf, "L", nL1, nL2, 500, 1000, 10);
            Link lLB = createLink(nf, "LB", nL2, nB1, 5000, 2000, 25);
            Link lB = createLink(nf, "B", nB1, nB2, 500, 1000, 10);
            Link lBT = createLink(nf, "BT", nB2, nT1, 5000, 2000, 25);
            Link lT = createLink(nf, "T", nT1, nT2, 500, 1000, 10);
            Link lTL = createLink(nf, "TL", nT2, nL1, 5000, 2000, 25);

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

        private void createPopulation() {
            Population pop = this.scenario.getPopulation();
            PopulationFactory pf = pop.getFactory();

            Id<Person> personId = Id.create("1", Person.class);
            Person person = pf.createPerson(personId);
            Plan plan = pf.createPlan();
            person.addPlan(plan);

            Coord homeCoord = new Coord(545000, 160000);
            Coord workCoord = new Coord(600000, 195000);
            Coord shopCoord = new Coord(615000, 175000);

            Activity home1 = pf.createActivityFromCoord("home", homeCoord);
            home1.setEndTime(7*3600);
            home1.setLinkId(Id.create("L", Link.class));

            Activity work1 = pf.createActivityFromCoord("work", workCoord);
            work1.setEndTime(12*3600);
            work1.setLinkId(Id.create("B", Link.class));

            Activity shop1 = pf.createActivityFromCoord("shop", shopCoord);
            shop1.setEndTime(13*3600);
            shop1.setLinkId(Id.create("T", Link.class));

            Activity home2 = pf.createActivityFromCoord("home", homeCoord);
            home2.setEndTime(15*3600);
            home2.setLinkId(Id.create("L", Link.class));

            Activity work2 = pf.createActivityFromCoord("work", workCoord);
            work2.setEndTime(18*3600);
            work2.setLinkId(Id.create("B", Link.class));

            Activity home3 = pf.createActivityFromCoord("home", homeCoord);
            home3.setLinkId(Id.create("L", Link.class));

            plan.addActivity(home1);
            plan.addLeg(pf.createLeg("car"));
            plan.addActivity(work1);
            plan.addLeg(pf.createLeg("car"));
            plan.addActivity(shop1);
            plan.addLeg(pf.createLeg("car"));
            plan.addActivity(work2);
            plan.addLeg(pf.createLeg("car"));
            plan.addActivity(home3);

            pop.addPerson(person);
        }

        private void loadZones() {
            ZonesListConfigGroup zonesConfig = ConfigUtils.addOrGetModule(this.config, ZonesListConfigGroup.class);
            ZonesLoader.loadAllZones(zonesConfig, this.zones);
        }

        private void prepareEvents() {
            this.events = EventsUtils.createEventsManager(this.config);
        }

    }

}
