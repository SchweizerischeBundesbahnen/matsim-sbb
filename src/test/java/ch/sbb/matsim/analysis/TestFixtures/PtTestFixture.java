package ch.sbb.matsim.analysis.TestFixtures;

import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.config.SBBTransitConfigGroup;
import ch.sbb.matsim.mobsim.qsim.pt.SBBTransitEngineQSimModule;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
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
import org.matsim.core.events.EventsUtils;
import org.matsim.core.mobsim.qsim.ActivityEngineModule;
import org.matsim.core.mobsim.qsim.PopulationModule;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.QSimBuilder;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.routes.DefaultTransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.testcases.utils.EventsCollector;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;

public class PtTestFixture {

	public Scenario scenario;
	public Config config;
	public EventsManager eventsManager;
	TransitStopFacility stopA;
	TransitStopFacility stopB;
	TransitStopFacility stopC;
	TransitStopFacility stopD;
	TransitStopFacility stopE;
	TransitLine line1;
	TransitRoute route1;

	public PtTestFixture() {
		this.config = ConfigUtils.createConfig(new PostProcessingConfigGroup());
		this.config.transit().setUseTransit(true);
		SBBTransitConfigGroup sbbConfig = ConfigUtils.addOrGetModule(this.config, SBBTransitConfigGroup.class);
		sbbConfig.setDeterministicServiceModes(Collections.singleton("train"));
		this.scenario = ScenarioUtils.createScenario(config);

		this.eventsManager = EventsUtils.createEventsManager(config);

		createNetwork();
	}

	public void addSingleTransitDemand() {
		Population population = this.scenario.getPopulation();
		PopulationFactory pf = population.getFactory();
		Person person = pf.createPerson(Id.create(1, Person.class));
		person.getAttributes().putAttribute("subpopulation", "regular");
		Plan plan = pf.createPlan();
		Activity act1 = pf.createActivityFromLinkId("home", Id.create(1, Link.class));
		act1.setEndTime(29500);
		Leg leg = pf.createLeg("pt");
		DefaultTransitPassengerRoute route = new DefaultTransitPassengerRoute(this.stopB, this.line1, this.route1, this.stopD);
		route.setBoardingTime(29500);
		leg.setRoute(route);

		Activity act2 = pf.createActivityFromLinkId("work", Id.create(3, Link.class));

		plan.addActivity(act1);
		plan.addLeg(leg);
		plan.addActivity(act2);
		person.addPlan(plan);
		population.addPerson(person);
	}

	private void createNetwork() {
		Network network = this.scenario.getNetwork();
		NetworkFactory nf = network.getFactory();

		Node node1 = nf.createNode(Id.create(1, Node.class), new Coord(10000, 0));
		Node node2 = nf.createNode(Id.create(2, Node.class), new Coord(15000, 0));
		Node node3 = nf.createNode(Id.create(3, Node.class), new Coord(25000, 0));
		Node node4 = nf.createNode(Id.create(4, Node.class), new Coord(35000, 0));
		Node node5 = nf.createNode(Id.create(5, Node.class), new Coord(40000, 0));

		network.addNode(node1);
		network.addNode(node2);
		network.addNode(node3);
		network.addNode(node4);
		network.addNode(node5);

		Link link1 = createLink(nf, 1, 7500, node1, node2);
		Link link2 = createLink(nf, 2, 1200, node2, node3);
		Link link3 = createLink(nf, 3, 1200, node3, node4);
		Link link4 = createLink(nf, 4, 6500, node4, node5);

		network.addLink(link1);
		network.addLink(link2);
		network.addLink(link3);
		network.addLink(link4);

		TransitSchedule schedule = this.scenario.getTransitSchedule();
		Vehicles vehicles = this.scenario.getTransitVehicles();
		TransitScheduleFactory f = schedule.getFactory();
		VehiclesFactory vf = vehicles.getFactory();

		VehicleType vehType1 = vf.createVehicleType(Id.create("some_train", VehicleType.class));
		VehicleCapacity vehCapacity = vehType1.getCapacity();
		vehCapacity.setSeats(300);
		vehCapacity.setStandingRoom(150);
		vehicles.addVehicleType(vehType1);
		VehicleUtils.setDoorOperationMode(vehType1, VehicleType.DoorOperationMode.serial);
		VehicleUtils.setAccessTime(vehType1, 2); // 1 person takes 2 seconds to board
		VehicleUtils.setEgressTime(vehType1, 2);
		Vehicle veh1 = vf.createVehicle(Id.create("train1", Vehicle.class), vehType1);
		vehicles.addVehicle(veh1);

		this.stopA = f.createTransitStopFacility(Id.create("A", TransitStopFacility.class), node1.getCoord(), false);
		this.stopB = f.createTransitStopFacility(Id.create("B", TransitStopFacility.class), node2.getCoord(), false);
		this.stopC = f.createTransitStopFacility(Id.create("C", TransitStopFacility.class), node3.getCoord(), false);
		this.stopD = f.createTransitStopFacility(Id.create("D", TransitStopFacility.class), node4.getCoord(), false);
		this.stopE = f.createTransitStopFacility(Id.create("E", TransitStopFacility.class), node5.getCoord(), false);

		this.stopA.setLinkId(link1.getId());
		this.stopB.setLinkId(link1.getId());
		this.stopC.setLinkId(link2.getId());
		this.stopD.setLinkId(link3.getId());
		this.stopE.setLinkId(link4.getId());

		schedule.addStopFacility(this.stopA);
		schedule.addStopFacility(this.stopB);
		schedule.addStopFacility(this.stopC);
		schedule.addStopFacility(this.stopD);
		schedule.addStopFacility(this.stopE);

		this.line1 = f.createTransitLine(Id.create("S2016_1", TransitLine.class));

		List<Id<Link>> linkIdList = new ArrayList<>();
		linkIdList.add(link2.getId());
		linkIdList.add(link3.getId());
		NetworkRoute networkRoute = RouteUtils.createLinkNetworkRouteImpl(link1.getId(), linkIdList, link4.getId());

		List<TransitRouteStop> stops = new ArrayList<>(5);
		stops.add(f.createTransitRouteStopBuilder(this.stopA).departureOffset(0.0).build());
		stops.add(f.createTransitRouteStop(this.stopB, 100, 120.0));
		stops.add(f.createTransitRouteStopBuilder(this.stopC).departureOffset(300.).build());
		stops.add(f.createTransitRouteStop(this.stopD, 570, 600.0));
		stops.add(f.createTransitRouteStopBuilder(this.stopE).arrivalOffset(720.).build());

		this.route1 = f.createTransitRoute(Id.create("A2E", TransitRoute.class), networkRoute, stops, "train");

		Departure departure1 = f.createDeparture(Id.create(1, Departure.class), 30000.0);
		departure1.setVehicleId(veh1.getId());
		this.route1.addDeparture(departure1);

		this.line1.addRoute(this.route1);
		schedule.addTransitLine(this.line1);

		addRouteAttributes(this.line1.getId(), this.route1.getId(), "route1");
		addStopsAttributes(this.stopA.getId(), "A");
		addStopsAttributes(this.stopB.getId(), "B");
		addStopsAttributes(this.stopC.getId(), "C");
		addStopsAttributes(this.stopD.getId(), "D");
		addStopsAttributes(this.stopE.getId(), "E");
	}

	private void addRouteAttributes(Id lineId, Id routeId, String name) {
		scenario.getTransitSchedule().getTransitLines().get(lineId).getRoutes().get(routeId).getAttributes().putAttribute("04_DirectionCode", "code");
		scenario.getTransitSchedule().getTransitLines().get(lineId).getRoutes().get(routeId).getAttributes().putAttribute("09_TSysCode", "code");
		scenario.getTransitSchedule().getTransitLines().get(lineId).getRoutes().get(routeId).getAttributes().putAttribute("02_TransitLine", "code");
		scenario.getTransitSchedule().getTransitLines().get(lineId).getRoutes().get(routeId).getAttributes().putAttribute("03_LineRouteName", "code");
		scenario.getTransitSchedule().getTransitLines().get(lineId).getRoutes().get(routeId).getAttributes().putAttribute("05_Name", "code");
	}

	private void addStopsAttributes(Id stopId, String name) {
		scenario.getTransitSchedule().getFacilities().get(stopId).getAttributes().putAttribute("02_Stop_No", name);
	}

	private Link createLink(NetworkFactory nf, int id, double length, Node fromNode, Node toNode) {
		Link link = nf.createLink(Id.create(id, Link.class), fromNode, toNode);
		link.setLength(length);
		link.setFreespeed(33.3);
		link.setCapacity(1000);
		link.setNumberOfLanes(1);
		return link;
	}

	public void addEvents() {
		QSim qSim = new QSimBuilder(config) //
				.addQSimModule(new ActivityEngineModule())
				.addQSimModule(new PopulationModule())
				.addQSimModule(new SBBTransitEngineQSimModule())
				.addQSimModule(new TestQSimModule(config))
				.configureQSimComponents(configurator -> {
					SBBTransitEngineQSimModule.configure(configurator);
					configurator.addNamedComponent(ActivityEngineModule.COMPONENT_NAME);
					configurator.addNamedComponent(PopulationModule.COMPONENT_NAME);
				})
				.build(scenario, eventsManager);

		EventsCollector collector = new EventsCollector();
		eventsManager.addHandler(collector);
		qSim.run();

	}

}
