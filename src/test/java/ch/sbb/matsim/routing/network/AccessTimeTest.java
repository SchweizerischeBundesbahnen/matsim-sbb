package ch.sbb.matsim.routing.network;

import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
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
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.config.groups.StrategyConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.scenario.ScenarioUtils;


public class AccessTimeTest {

    private Coord bern = new Coord(600000, 200000);
    private Coord stleo = new Coord(598345.54, 122581.99);
    private Coord thun = new Coord(613843.82, 178094.54);
    private String shapefile = "\\\\V00925\\Simba\\10_Daten\\70_Geodaten\\400_Geodaten\\Raumgliederung_CH\\BFS_CH14\\BFS_CH14_Gemeinden.shp";


    @Test
    public final void testCar() {
        Controler controler = makeScenario(bern, stleo);
        controler.run();

        Person person = controler.getScenario().getPopulation().getPersons().get(Id.createPersonId("1"));
        for(PlanElement pe :person.getSelectedPlan().getPlanElements()){
            if(pe instanceof Leg){
                Leg leg = (Leg) pe;
                System.out.println(leg.getTravelTime());
            }
        }
    }

    private Controler makeScenario(Coord start, Coord end){
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        Population population = scenario.getPopulation();
        Network network = scenario.getNetwork();

        Node nodeA = network.getFactory().createNode(Id.createNodeId("a"), start);
        Node nodeB = network.getFactory().createNode(Id.createNodeId("b"), end);

        network.addNode(nodeA);
        network.addNode(nodeB);

        Link linkAB = network.getFactory().createLink(Id.createLinkId("ab"), nodeA, nodeB);

        network.addLink(linkAB);

        PopulationFactory populationFactory = population.getFactory();
        Plan plan = populationFactory.createPlan();

        Person person = populationFactory.createPerson(Id.createPersonId("1"));

        Activity home = populationFactory.createActivityFromCoord("home", start);
        home.setEndTime(8*60*60);
        plan.addActivity(home);
        Leg leg = populationFactory.createLeg("car");

        plan.addLeg(leg);

        Activity work = populationFactory.createActivityFromCoord("work", end);
        work.setStartTime(10*60*60);

        plan.addActivity(work);
        person.addPlan(plan);
        person.setSelectedPlan(plan);

        population.addPerson(person);

        PlanCalcScoreConfigGroup.ActivityParams params = new PlanCalcScoreConfigGroup.ActivityParams("home" ) ;
        scenario.getConfig().planCalcScore().addActivityParams(params);

        PlanCalcScoreConfigGroup.ActivityParams params2 = new PlanCalcScoreConfigGroup.ActivityParams("work" ) ;
        scenario.getConfig().planCalcScore().addActivityParams(params2);

        scenario.getConfig().plansCalcRoute().setInsertingAccessEgressWalk(true);


        StrategyConfigGroup.StrategySettings settings = new StrategyConfigGroup.StrategySettings();
        settings.setStrategyName(DefaultPlanStrategiesModule.DefaultStrategy.TimeAllocationMutator_ReRoute.toString());
        settings.setWeight(1.0);
        scenario.getConfig().strategy().addStrategySettings(settings);

        Controler controler = new Controler(scenario);

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                addRoutingModuleBinding(TransportMode.car).toProvider(new SBBNetworkRouter(TransportMode.car, shapefile));
            }});

        controler.getConfig().controler().setOverwriteFileSetting(OutputDirectoryHierarchy.OverwriteFileSetting.deleteDirectoryIfExists);
        controler.getConfig().controler().setLastIteration(1);
        controler.getConfig().controler().setWriteEventsUntilIteration(1);
        controler.getConfig().controler().setWritePlansInterval(1);

        return controler;
    }
}
