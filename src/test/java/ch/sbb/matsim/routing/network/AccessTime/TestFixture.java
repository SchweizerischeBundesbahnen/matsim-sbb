package ch.sbb.matsim.routing.network.AccessTime;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

import ch.sbb.matsim.routing.network.SBBNetworkRoutingInclAccessEgressModule;
import com.google.inject.Inject;
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
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.Injector;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.TripRouter;
import org.matsim.core.router.TripRouterModule;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutilityFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.scenario.ScenarioByInstanceModule;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.facilities.ActivityFacility;
import org.matsim.facilities.FacilitiesUtils;
import org.matsim.facilities.Facility;
import org.matsim.testcases.utils.EventsCollector;

import ch.sbb.matsim.config.AccessTimeConfigGroup;
import ch.sbb.matsim.routing.access.AccessEgress;
import ch.sbb.matsim.scoring.SBBScoringFunctionFactory;

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
        accessTimeConfigGroup.setModesWithAccessTime(modesWithAccess);

        config.controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        config.controler().setLastIteration(0);
        config.controler().setWriteEventsUntilIteration(1);
        config.controler().setWritePlansInterval(1);
        config.qsim().setEndTime(10 * 60 * 60);
    }


    public double scoreRoute(Coord from, Coord to, RoutingModule router) {
        Controler _controler = new Controler(scenario);
        AccessEgress ae = new AccessEgress(_controler, shapefile);
        Facility fromFacility = FacilitiesUtils.createActivityFacilities().getFactory().createActivityFacility(Id.create("from", ActivityFacility.class), from);
        Facility toFacility = FacilitiesUtils.createActivityFacilities().getFactory().createActivityFacility(Id.create("to", ActivityFacility.class), to);
        double departureTime = 6 * 60 * 60;
        List<? extends PlanElement> elements = router.calcRoute(fromFacility, toFacility, departureTime, person);
        return getScoreOfRoute(elements);
    }

    private double getScoreOfRoute(List<? extends PlanElement> elements) {
        SBBScoringFunctionFactory fact = new SBBScoringFunctionFactory(scenario);
        ScoringFunction sf = fact.createNewScoringFunction(person);

        for (PlanElement pe : elements) {
            if (pe instanceof Leg) {
                sf.handleLeg((Leg) pe);
            } else if (pe instanceof Activity) {
                sf.handleActivity((Activity) pe);
            }

        }

        return sf.getScore();

    }

    public void run() {
        controler = new Controler(scenario);
        new AccessEgress(controler, shapefile).installAccessTime();

        EventsCollector collector = new EventsCollector();
        controler.getEvents().addHandler(collector);

        controler.run();

        allEvents = collector.getEvents();

        for (Event event : allEvents) {
            System.out.println(event.toString());
        }


    }

}
