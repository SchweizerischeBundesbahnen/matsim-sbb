package ch.sbb.matsim.routing.network.AccessTime;

import ch.sbb.matsim.config.SBBAccessTimeConfigGroup;
import ch.sbb.matsim.config.ZonesListConfigGroup;
import ch.sbb.matsim.routing.access.AccessEgress;
import ch.sbb.matsim.zones.ZonesModule;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.testcases.utils.EventsCollector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TestFixture {
    private String shapefile = "src/test/resources/shapefiles/AccessTime/accesstime_zone.SHP";

    Scenario scenario;
    Population population;
    Controler controler;

    List<Event> allEvents;

    PlanCalcScoreConfigGroup.ModeParams accessParams;
    PlanCalcScoreConfigGroup.ModeParams egressParams;

    Activity home;
    Activity work;

    Person person;

    TestFixture(Coord start, Coord end, String mode, boolean withAccess, double constant, String modesWithAccess) {


        Config config = ConfigUtils.createConfig(new SBBAccessTimeConfigGroup());
        scenario = ScenarioUtils.createScenario(config);

        population = scenario.getPopulation();
        Network network = scenario.getNetwork();

        Double delta_x = end.getX()-start.getX();
        Double delta_y = end.getY()-start.getY();
        Node nodeA = network.getFactory().createNode(Id.createNodeId("a"), start);
        Node nodeB = network.getFactory().createNode(Id.createNodeId("b"), new Coord(start.getX() + delta_x/2.0, start.getY() + delta_y/2.0));
        Node nodeC = network.getFactory().createNode(Id.createNodeId("c"), new Coord(start.getX() + delta_x/2.0*3.0, start.getY() + delta_y/2.0*3.0));
        Node nodeD = network.getFactory().createNode(Id.createNodeId("d"), end);

        network.addNode(nodeA);
        network.addNode(nodeB);
        network.addNode(nodeC);
        network.addNode(nodeD);

        Link linkAB = network.getFactory().createLink(Id.createLinkId("ab"), nodeA, nodeB);
        Link linkBC = network.getFactory().createLink(Id.createLinkId("bc"), nodeB, nodeC);
        Link linkCD = network.getFactory().createLink(Id.createLinkId("cd"), nodeC, nodeD);

        Link linkBA = network.getFactory().createLink(Id.createLinkId("ba"), nodeB, nodeA);
        Link linkCB = network.getFactory().createLink(Id.createLinkId("cb"), nodeC, nodeB);
        Link linkDC = network.getFactory().createLink(Id.createLinkId("dc"), nodeD, nodeC);


        linkAB.setFreespeed(200.0);
        linkBA.setFreespeed(200.0);
        linkBC.setFreespeed(200.0);
        linkCB.setFreespeed(200.0);
        linkCD.setFreespeed(200.0);
        linkDC.setFreespeed(200.0);

        network.addLink(linkAB);
        network.addLink(linkBC);
        network.addLink(linkCD);

        network.addLink(linkBA);
        network.addLink(linkCB);
        network.addLink(linkDC);

        Set<String> linkModes = new HashSet<>();
        linkModes.add(mode);

        for (Link link : network.getLinks().values()) {
            link.setAllowedModes(linkModes);
        }

        PopulationFactory populationFactory = population.getFactory();
        Plan plan = populationFactory.createPlan();

        person = populationFactory.createPerson(Id.createPersonId("1"));

        home = populationFactory.createActivityFromCoord("home", start);
        home.setEndTime(6 * 60 * 60);
        plan.addActivity(home);
        Leg leg = populationFactory.createLeg(mode);

        plan.addLeg(leg);

        work = populationFactory.createActivityFromCoord("work", end);
        work.setStartTime(6 * 60 * 60);
        work.setEndTime(8 * 60 * 60);
        plan.addActivity(work);

        Leg leg2 = populationFactory.createLeg(mode);
        plan.addLeg(leg2);


        Activity home2 = populationFactory.createActivityFromCoord("home", start);
        home2.setStartTime(8 * 60 * 60);
        home2.setEndTime(10 * 60 * 60);
        plan.addActivity(home2);


        person.addPlan(plan);
        person.setSelectedPlan(plan);

        population.addPerson(person);

        PlanCalcScoreConfigGroup.ActivityParams params = new PlanCalcScoreConfigGroup.ActivityParams("home");
        params.setScoringThisActivityAtAll(false);
        scenario.getConfig().planCalcScore().addActivityParams(params);

        PlanCalcScoreConfigGroup.ActivityParams params2 = new PlanCalcScoreConfigGroup.ActivityParams("work");
        params2.setScoringThisActivityAtAll(false);
        scenario.getConfig().planCalcScore().addActivityParams(params2);

        PlanCalcScoreConfigGroup.ActivityParams params3 = new PlanCalcScoreConfigGroup.ActivityParams(mode + " interaction");
        params3.setScoringThisActivityAtAll(false);
        scenario.getConfig().planCalcScore().addActivityParams(params3);

        accessParams = config.planCalcScore().getOrCreateModeParams(TransportMode.non_network_walk);
        accessParams.setConstant(constant);

        egressParams = config.planCalcScore().getOrCreateModeParams(TransportMode.non_network_walk);
        egressParams.setConstant(constant);

        StrategyConfigGroup.StrategySettings settings = new StrategyConfigGroup.StrategySettings();
        settings.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator);
        settings.setWeight(1.0);
        scenario.getConfig().strategy().addStrategySettings(settings);

        ZonesListConfigGroup zonesConfigGroup = ConfigUtils.addOrGetModule(config, ZonesListConfigGroup.class);
        zonesConfigGroup.addZones(new ZonesListConfigGroup.ZonesParameterSet("zones", shapefile, null));

        SBBAccessTimeConfigGroup accessTimeConfigGroup = ConfigUtils.addOrGetModule(config, SBBAccessTimeConfigGroup.GROUP_NAME, SBBAccessTimeConfigGroup.class);
        accessTimeConfigGroup.setInsertingAccessEgressWalk(withAccess);
        accessTimeConfigGroup.setModesWithAccessTime(modesWithAccess);
        accessTimeConfigGroup.setZonesId("zones");

        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        config.controler().setLastIteration(0);
        config.controler().setWriteEventsUntilIteration(1);
        config.controler().setWritePlansInterval(1);
        config.qsim().setEndTime(10 * 60 * 60);
    }

    public void run() {
        controler = new Controler(scenario);

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                addTravelTimeBinding("ride").to(networkTravelTime());
                addTravelDisutilityFactoryBinding("ride").to(carTravelDisutilityFactoryKey());
            }
        });

        controler.addOverridingModule(new ZonesModule());
        controler.addOverridingModule(new AccessEgress(scenario));

        EventsCollector collector = new EventsCollector();
        controler.getEvents().addHandler(collector);

        controler.run();

        allEvents = collector.getEvents();

        for (Event event : allEvents) {
            System.out.println(event.toString());
        }


    }

}
