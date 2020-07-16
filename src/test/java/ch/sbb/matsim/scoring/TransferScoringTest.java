package ch.sbb.matsim.scoring;

import ch.sbb.matsim.config.SBBBehaviorGroupsConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes;
import java.util.ArrayList;
import java.util.List;
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
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.PlanCalcScoreConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.pt.PtConstants;
import org.matsim.pt.routes.DefaultTransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.testcases.MatsimTestUtils;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleCapacity;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;

/**
 * Main idea of the test: Run a mini-scenario with a single agent twice, once with default MATSim scoring, once with SBB Scoring. Then we can calculate the score-difference and compare it against our
 * expectations.
 *
 * @author mrieser
 */
public class TransferScoringTest {

	private static final Logger log = Logger.getLogger(TransferScoringTest.class);

	@Rule
	public MatsimTestUtils helper = new MatsimTestUtils();

	@Test
	public void testTransferScoring() {
		double score1;
		double score2;
		{
			Fixture f1 = new Fixture(this.helper.getOutputDirectory() + "/run1/");
			f1.setLineSwitchConfig(-3.0);

			f1.config.controler().setLastIteration(0);

			Controler controler = new Controler(f1.scenario);
			controler.run();

			Person p1 = f1.scenario.getPopulation().getPersons().get(Id.create(1, Person.class));
			Plan plan1 = p1.getSelectedPlan();
			score1 = plan1.getScore();
		}
		{
			Fixture f2 = new Fixture(this.helper.getOutputDirectory() + "/run2/");
			f2.setSBBTransferUtility(-1.0, -2.0, -12.0, -2.0);

			f2.config.controler().setLastIteration(0);

			Controler controler = new Controler(f2.scenario);
			controler.setScoringFunctionFactory(new SBBScoringFunctionFactory(f2.scenario));
			controler.run();

			Person p1 = f2.scenario.getPopulation().getPersons().get(Id.create(1, Person.class));
			Plan plan1 = p1.getSelectedPlan();
			score2 = plan1.getScore();
		}

		log.info("score with default scoring: " + score1);
		log.info("score with sbb-scoring: " + score2);

		double actualScoreDiff = score2 - score1;
		double defaultTransferScore = -3.0;
		double sbbTransferScore = Math.max(-12.0, Math.min(-2.0, -1.0 - 2.0 * (360.0 / 3600.0)));
		double expectedScoreDiff = sbbTransferScore - defaultTransferScore;

		Assert.assertEquals(expectedScoreDiff, actualScoreDiff, 1e-7);
	}

	private static class Fixture {

		Config config;
		Scenario scenario;

		Fixture(String outputDirectory) {
			this.config = ConfigUtils.createConfig();
			prepareConfig(outputDirectory);
			this.scenario = ScenarioUtils.createScenario(this.config);
			createNetwork();
			createTransitSchedule();
			createPopulation();
		}

		private void prepareConfig(String outputDirectory) {
			PlanCalcScoreConfigGroup scoringConfig = this.config.planCalcScore();
			PlanCalcScoreConfigGroup.ActivityParams homeScoring = new PlanCalcScoreConfigGroup.ActivityParams("home");
			homeScoring.setTypicalDuration(12 * 3600);
			scoringConfig.addActivityParams(homeScoring);

			this.config.controler().setOutputDirectory(outputDirectory);
			this.config.controler().setCreateGraphs(false);
			this.config.controler().setDumpDataAtEnd(false);

			this.config.transit().setUseTransit(true);

			PlanCalcScoreConfigGroup.ActivityParams params = new PlanCalcScoreConfigGroup.ActivityParams("ride interaction");
			params.setScoringThisActivityAtAll(false);
			config.planCalcScore().getOrCreateScoringParameters(null).addActivityParams(params);
		}

		void setLineSwitchConfig(double lineSwitchUtility) {
			this.config.planCalcScore().setUtilityOfLineSwitch(lineSwitchUtility);
		}

		void setSBBTransferUtility(double baseTransferUtility, double marginalTransferUtility, double minTransferUtility, double maxTransferUtility) {
			SBBBehaviorGroupsConfigGroup sbbConfig = ConfigUtils.addOrGetModule(this.config, SBBBehaviorGroupsConfigGroup.class);

			sbbConfig.setBaseTransferUtility(baseTransferUtility);
			sbbConfig.setTransferUtilityPerTravelTime_utils_hr(marginalTransferUtility);
			sbbConfig.setMinimumTransferUtility(minTransferUtility);
			sbbConfig.setMaximumTransferUtility(maxTransferUtility);
		}

		private void createNetwork() {
			Network network = this.scenario.getNetwork();
			NetworkFactory nf = network.getFactory();

			Node n1 = nf.createNode(Id.create("1", Node.class), new Coord(1000, 1000));
			Node n2 = nf.createNode(Id.create("2", Node.class), new Coord(3000, 1000));
			Node n3 = nf.createNode(Id.create("3", Node.class), new Coord(5000, 1000));
			Node n4 = nf.createNode(Id.create("4", Node.class), new Coord(7000, 1000));
			Node n5 = nf.createNode(Id.create("5", Node.class), new Coord(9000, 1000));
			Node n6 = nf.createNode(Id.create("6", Node.class), new Coord(9900, 1000));

			network.addNode(n1);
			network.addNode(n2);
			network.addNode(n3);
			network.addNode(n4);
			network.addNode(n5);
			network.addNode(n6);

			Link l1 = createLink(nf, "1", n1, n2, 4000, 1000, 25);
			Link l2 = createLink(nf, "2", n2, n3, 4000, 1000, 25);
			Link l3 = createLink(nf, "3", n3, n4, 4000, 1000, 25);
			Link l4 = createLink(nf, "4", n4, n5, 4000, 1000, 25);
			Link l5 = createLink(nf, "5", n5, n6, 4000, 1000, 25);

			network.addLink(l1);
			network.addLink(l2);
			network.addLink(l3);
			network.addLink(l4);
			network.addLink(l5);
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

		private void createTransitSchedule() {
			Vehicles vehicles = this.scenario.getTransitVehicles();
			VehiclesFactory vf = vehicles.getFactory();
			VehicleType vt = vf.createVehicleType(Id.create("train", VehicleType.class));
			VehicleCapacity vc = vt.getCapacity();
			vc.setSeats(100);
			vehicles.addVehicleType(vt);
			vehicles.addVehicle(vf.createVehicle(Id.create("b1", Vehicle.class), vt));
			vehicles.addVehicle(vf.createVehicle(Id.create("r1", Vehicle.class), vt));

			TransitSchedule schedule = this.scenario.getTransitSchedule();
			TransitScheduleFactory sf = schedule.getFactory();

			TransitStopFacility stop1 = sf.createTransitStopFacility(Id.create("1", TransitStopFacility.class), new Coord(3000, 1000), false);
			stop1.setLinkId(Id.create("1", Link.class));
			schedule.addStopFacility(stop1);

			TransitStopFacility stop2 = sf.createTransitStopFacility(Id.create("2", TransitStopFacility.class), new Coord(5000, 1000), false);
			stop2.setLinkId(Id.create("2", Link.class));
			schedule.addStopFacility(stop2);

			TransitStopFacility stop3 = sf.createTransitStopFacility(Id.create("3", TransitStopFacility.class), new Coord(7000, 1000), false);
			stop3.setLinkId(Id.create("3", Link.class));
			schedule.addStopFacility(stop3);

			TransitLine blueLine = sf.createTransitLine(Id.create("blue", TransitLine.class));
			NetworkRoute blueNetRoute = RouteUtils.createLinkNetworkRouteImpl(Id.create(1, Link.class), Id.create(2, Link.class));
			List<TransitRouteStop> blueStops = new ArrayList<>();
			blueStops.add(sf.createTransitRouteStopBuilder(stop1).departureOffset(0.).build());
			blueStops.add(sf.createTransitRouteStopBuilder(stop2).arrivalOffset(120.0).build());
			TransitRoute blueRoute = sf.createTransitRoute(Id.create("blue1", TransitRoute.class), blueNetRoute, blueStops, "train");
			Departure blueDeparture = sf.createDeparture(Id.create(1, Departure.class), 8 * 3600);
			blueDeparture.setVehicleId(Id.create("b1", Vehicle.class));
			blueRoute.addDeparture(blueDeparture);

			blueLine.addRoute(blueRoute);
			schedule.addTransitLine(blueLine);

			TransitLine redLine = sf.createTransitLine(Id.create("red", TransitLine.class));
			NetworkRoute redNetRoute = RouteUtils.createLinkNetworkRouteImpl(Id.create(2, Link.class), Id.create(3, Link.class));
			List<TransitRouteStop> redStops = new ArrayList<>();
			redStops.add(sf.createTransitRouteStopBuilder(stop2).departureOffset(0.).build());
			redStops.add(sf.createTransitRouteStopBuilder(stop3).arrivalOffset(120.).build());
			TransitRoute redRoute = sf.createTransitRoute(Id.create("red1", TransitRoute.class), redNetRoute, redStops, "train");
			Departure redDeparture = sf.createDeparture(Id.create(1, Departure.class), 8 * 3600 + 240);
			redDeparture.setVehicleId(Id.create("r1", Vehicle.class));
			redRoute.addDeparture(redDeparture);
			redLine.addRoute(redRoute);
			schedule.addTransitLine(redLine);
		}

		private void createPopulation() {
			TransitSchedule schedule = this.scenario.getTransitSchedule();
			TransitStopFacility stop1 = schedule.getFacilities().get(Id.create(1, TransitStopFacility.class));
			TransitStopFacility stop2 = schedule.getFacilities().get(Id.create(2, TransitStopFacility.class));
			TransitStopFacility stop3 = schedule.getFacilities().get(Id.create(3, TransitStopFacility.class));
			TransitLine blueLine = schedule.getTransitLines().get(Id.create("blue", TransitLine.class));
			TransitRoute blueRoute = blueLine.getRoutes().get(Id.create("blue1", TransitRoute.class));
			TransitLine redLine = schedule.getTransitLines().get(Id.create("red", TransitLine.class));
			TransitRoute redRoute = redLine.getRoutes().get(Id.create("red1", TransitRoute.class));

			Population pop = this.scenario.getPopulation();
			PopulationFactory pf = pop.getFactory();

			Id<Person> personId = Id.create("1", Person.class);
			Person person = pf.createPerson(personId);
			Plan plan = pf.createPlan();
			person.addPlan(plan);

			Coord home1Coord = new Coord(1000, 900);
			Coord home2Coord = new Coord(9900, 900);

			Activity home1 = pf.createActivityFromCoord("home", home1Coord);
			home1.setEndTime(8 * 3600 - 600);
			home1.setLinkId(Id.create("1", Link.class));

			Activity home2 = pf.createActivityFromCoord("home", home2Coord);
			home2.setLinkId(Id.create("4", Link.class));

			Activity ptAct1 = pf.createActivityFromCoord(PtConstants.TRANSIT_ACTIVITY_TYPE, new Coord(3000, 1000));
			ptAct1.setLinkId(Id.create(1, Link.class));
			ptAct1.setMaximumDuration(0.0);
			Activity transferAct = pf.createActivityFromCoord(PtConstants.TRANSIT_ACTIVITY_TYPE, new Coord(5000, 1000));
			transferAct.setLinkId(Id.create(2, Link.class));
			transferAct.setMaximumDuration(0.0);
			Activity ptAct2 = pf.createActivityFromCoord(PtConstants.TRANSIT_ACTIVITY_TYPE, new Coord(7000, 1000));
			ptAct2.setLinkId(Id.create(3, Link.class));
			ptAct2.setMaximumDuration(0.0);

			plan.addActivity(home1);
			Leg accessLeg = pf.createLeg(SBBModes.ACCESS_EGRESS_WALK);
			accessLeg.setRoute(RouteUtils.createGenericRouteImpl(Id.create("1", Link.class), Id.create("1", Link.class)));
			accessLeg.getRoute().setDistance(200);
			accessLeg.getRoute().setTravelTime(300);
			plan.addLeg(accessLeg);
			plan.addActivity(ptAct1);
			Leg pt1Leg = pf.createLeg(SBBModes.PT);
			pt1Leg.setRoute(new DefaultTransitPassengerRoute(stop1, blueLine, blueRoute, stop2));
			plan.addLeg(pt1Leg);
			plan.addActivity(transferAct);
			Leg pt2Leg = pf.createLeg(SBBModes.PT);
			pt2Leg.setRoute(new DefaultTransitPassengerRoute(stop2, redLine, redRoute, stop3));
			plan.addLeg(pt2Leg);
			plan.addActivity(ptAct2);
			Leg egressLeg = pf.createLeg(SBBModes.ACCESS_EGRESS_WALK);
			egressLeg.setRoute(RouteUtils.createGenericRouteImpl(Id.create("3", Link.class), Id.create("4", Link.class)));
			egressLeg.getRoute().setDistance(200);
			egressLeg.getRoute().setTravelTime(300);
			plan.addLeg(egressLeg);
			plan.addActivity(home2);
			TripStructureUtils.getLegs(plan).stream().forEach(leg -> TripStructureUtils.setRoutingMode(leg, SBBModes.PT));
			pop.addPerson(person);
		}

	}

}
