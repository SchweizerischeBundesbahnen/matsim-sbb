package ch.sbb.matsim.routing.network.AccessTime;

import java.util.List;

import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.testcases.utils.EventsCollector;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.config.AccessTimeConfigGroup;

public class TestFixture {
    private String shapefile = "src/test/resources/shapefiles/AccessTime/accesstime_zone.SHP";

    Scenario scenario;
    Population population;
    Controler controler;

    List<Event> allEvents;

    PlanCalcScoreConfigGroup.ModeParams accessParams;
    PlanCalcScoreConfigGroup.ModeParams egressParams;


    TestFixture(Coord start, Coord end, String mode, boolean withAccess, double constant) {

        Config config = ConfigUtils.createConfig(new AccessTimeConfigGroup());
        scenario = ScenarioUtils.createScenario(config);

        population = scenario.getPopulation();
        Network network = scenario.getNetwork();

        Double delta = 1.0;
        Node nodeA = network.getFactory().createNode(Id.createNodeId("a"), start);
        Node nodeB = network.getFactory().createNode(Id.createNodeId("c"), new Coord(start.getX() + delta, start.getY() + delta));
        Node nodeC = network.getFactory().createNode(Id.createNodeId("d"), new Coord(end.getX() + delta, end.getY() + delta));
        Node nodeD = network.getFactory().createNode(Id.createNodeId("b"), end);

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


        linkBC.setFreespeed(2.0);
        linkCB.setFreespeed(2.0);

        network.addLink(linkAB);
        network.addLink(linkBC);
        network.addLink(linkCD);

        network.addLink(linkBA);
        network.addLink(linkCB);
        network.addLink(linkDC);

        PopulationFactory populationFactory = population.getFactory();
        Plan plan = populationFactory.createPlan();

        Person person = populationFactory.createPerson(Id.createPersonId("1"));

        Activity home = populationFactory.createActivityFromCoord("home", start);
        home.setEndTime(6 * 60 * 60);
        plan.addActivity(home);
        Leg leg = populationFactory.createLeg(mode);

        plan.addLeg(leg);

        Activity work = populationFactory.createActivityFromCoord("work", end);
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

        accessParams = config.planCalcScore().getOrCreateModeParams(TransportMode.access_walk);
        accessParams.setConstant(constant);

        egressParams = config.planCalcScore().getOrCreateModeParams(TransportMode.egress_walk);
        egressParams.setConstant(constant);

        StrategyConfigGroup.StrategySettings settings = new StrategyConfigGroup.StrategySettings();
        settings.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator_ReRoute.toString());
        settings.setWeight(1.0);
        scenario.getConfig().strategy().addStrategySettings(settings);


        AccessTimeConfigGroup accessTimeConfigGroup = ConfigUtils.addOrGetModule(config, AccessTimeConfigGroup.GROUP_NAME, AccessTimeConfigGroup.class);

        accessTimeConfigGroup.setShapefile(shapefile);
        accessTimeConfigGroup.setInsertingAccessEgressWalk(withAccess);

        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        config.controler().setLastIteration(0);
        config.controler().setWriteEventsUntilIteration(1);
        config.controler().setWritePlansInterval(1);
        config.qsim().setEndTime(10 * 60 * 60);


    }

    public void run() {
        controler = new Controler(scenario);
        new RunSBB().installAccessTime(controler);


        EventsCollector collector = new EventsCollector();
        controler.getEvents().addHandler(collector);

        controler.run();

        allEvents = collector.getEvents();

        for (Event event : allEvents) {
            System.out.println(event.toString());
        }


    }

}
