package ch.sbb.matsim.analysis.TestFixtures;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.Event;
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
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.PrepareForSimUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.QSimBuilder;
import org.matsim.core.population.routes.LinkNetworkRouteFactory;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.testcases.utils.EventsCollector;

public class LinkTestFixture {

	public Scenario scenario;
	public Config config;
	public EventsManager eventsManager;
	private Link link1;
	private Link link2;
	private Link link3;
	private boolean qsimPrepared = false;

	public LinkTestFixture() {
		config = ConfigUtils.createConfig();
		config.qsim().setEndTime(35000);
		Scenario scenario = ScenarioUtils.createScenario(config);
		this.scenario = scenario;
		this.eventsManager = EventsUtils.createEventsManager(config);

		Network network = scenario.getNetwork();

		NetworkFactory nf = network.getFactory();

		Node node2 = nf.createNode(Id.create(2, Node.class), new Coord(15000, 0));
		Node node3 = nf.createNode(Id.create(3, Node.class), new Coord(25000, 0));
		Node node4 = nf.createNode(Id.create(4, Node.class), new Coord(35000, 0));
		Node node5 = nf.createNode(Id.create(5, Node.class), new Coord(40000, 0));

		network.addNode(node2);
		network.addNode(node3);
		network.addNode(node4);
		network.addNode(node5);

		link1 = createLink(nf, 2, 1200, node2, node3);
		link2 = createLink(nf, 3, 1200, node3, node4);
		link3 = createLink(nf, 4, 6500, node4, node5);

		network.addLink(link1);
		network.addLink(link2);
		network.addLink(link3);

	}

	private Link createLink(NetworkFactory nf, int id, double length, Node fromNode, Node toNode) {
		Link link = nf.createLink(Id.create(id, Link.class), fromNode, toNode);
		link.setLength(length);
		Set<String> mode = new HashSet<>(Arrays.asList("car", "ride"));
		link.setAllowedModes(mode);
		link.setFreespeed(34.3);
		link.setCapacity(1000);
		link.setNumberOfLanes(1);
		return link;
	}

	public void addEvents() {
		if (!this.qsimPrepared) {
			PrepareForSimUtils.createDefaultPrepareForSim(this.scenario).run();
		}
		QSim qSim = new QSimBuilder(this.config).useDefaults().build(scenario, this.eventsManager);

		EventsCollector collector = new EventsCollector();
		this.eventsManager.addHandler(collector);

		qSim.run();
		List<Event> allEvents = collector.getEvents();

		for (Event event : allEvents) {
			System.out.println(event.toString());
		}

	}

	public void addDemand() {
		Population population = this.scenario.getPopulation();
		PopulationFactory pf = population.getFactory();
		Person person = pf.createPerson(Id.create(1, Person.class));
		Plan plan = pf.createPlan();
		Activity act1 = pf.createActivityFromLinkId("home", link1.getId());
		act1.setEndTime(29500);
		Leg leg = pf.createLeg("car");

		LinkNetworkRouteFactory factory = new LinkNetworkRouteFactory();
		NetworkRoute route = (NetworkRoute) factory.createRoute(link1.getId(), link3.getId());
		route.setLinkIds(link1.getId(), List.of(link2.getId()), link3.getId());
		leg.setRoute(route);

		leg.setRoute(route);
		Activity act2 = pf.createActivityFromLinkId("work", link3.getId());

		plan.addActivity(act1);
		plan.addLeg(leg);
		plan.addActivity(act2);
		person.addPlan(plan);
		population.addPerson(person);
	}

}
