package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import junit.framework.TestCase;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.router.DefaultRoutingRequest;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.routes.TransitPassengerRoute;
import org.matsim.pt.transitSchedule.api.*;
import org.matsim.testcases.MatsimTestUtils;

import java.util.List;

public class SBBRaptorTransferCostCalculatorTest extends TestCase {

	private SwissRailRaptor createTransitRouter(TransitSchedule schedule, Config config, Network network) {
		SwissRailRaptorData data = SwissRailRaptorData.create(schedule, null, RaptorUtils.createStaticConfig(config), network, null);
		SwissRailRaptor raptor = new SwissRailRaptor.Builder(data, config).with(new SBBRaptorTransferCostCalculator()).build();
		return raptor;
	}

	@Test
	public void testNoModeToModePenalties() {
		Fixture f = new Fixture();
		f.init();
		RaptorParameters raptorParams = RaptorUtils.createParameters(f.config);
		TransitRouter router = createTransitRouter(f.schedule, f.config, f.network);
		Coord toCoord = new Coord(28100, 5050);
		List<? extends PlanElement> legs = router.calcRoute(DefaultRoutingRequest.withoutAttributes(new FakeFacility(new Coord(24100, 50)), new FakeFacility(toCoord), 6.0 * 3600, null));
		assertEquals(5, legs.size());
		assertEquals(TransportMode.walk, ((Leg) legs.get(0)).getMode());
		assertEquals(TransportMode.pt, ((Leg) legs.get(1)).getMode());
		assertEquals(TransportMode.walk, ((Leg) legs.get(2)).getMode());
		assertEquals(TransportMode.pt, ((Leg) legs.get(3)).getMode());
		assertEquals(TransportMode.walk, ((Leg) legs.get(4)).getMode());
		assertTrue("expected TransitRoute in leg.", ((Leg) legs.get(1)).getRoute() instanceof TransitPassengerRoute);
		TransitPassengerRoute ptRoute = (TransitPassengerRoute) ((Leg) legs.get(1)).getRoute();
		assertEquals(Id.create("22", TransitStopFacility.class), ptRoute.getAccessStopId());
		assertEquals(Id.create("18", TransitStopFacility.class), ptRoute.getEgressStopId());
		assertEquals(f.greenLine.getId(), ptRoute.getLineId());
		assertEquals(Id.create("green clockwise", TransitRoute.class), ptRoute.getRouteId());
		assertTrue("expected TransitRoute in leg.", ((Leg) legs.get(3)).getRoute() instanceof TransitPassengerRoute);
		ptRoute = (TransitPassengerRoute) ((Leg) legs.get(3)).getRoute();
		assertEquals(Id.create("18", TransitStopFacility.class), ptRoute.getAccessStopId());
		assertEquals(Id.create("21", TransitStopFacility.class), ptRoute.getEgressStopId());
		assertEquals(f.greenLine.getId(), ptRoute.getLineId());
		assertEquals(Id.create("green clockwise", TransitRoute.class), ptRoute.getRouteId());
		double actualTravelTime = 0.0;
		for (PlanElement leg : legs) {
			actualTravelTime += ((Leg) leg).getTravelTime().seconds();
			System.out.println(((Leg) leg).getTravelTime().seconds());
		}
		double expectedTravelTime = 3060 + // agent takes the *:06 course, arriving in C at *:18, departing at *:21, arriving in K at*:31
				CoordUtils.calcEuclideanDistance(f.schedule.getFacilities().get(Id.create("21", TransitStopFacility.class)).getCoord(), toCoord) / raptorParams.getBeelineWalkSpeed();
		expectedTravelTime = 3835;
		assertEquals(Math.ceil(expectedTravelTime), actualTravelTime, MatsimTestUtils.EPSILON);
	}

	@Test
	public void testNoModeToModePenaltiesBusBus() {
		Fixture f = new Fixture();
		f.init();
		ConfigUtils.addOrGetModule(f.config, SwissRailRaptorConfigGroup.class).addModeToModeTransferPenalty(new SwissRailRaptorConfigGroup.ModeToModeTransferPenalty("bus", "bus", 4.02));
		RaptorParameters raptorParams = RaptorUtils.createParameters(f.config);
		TransitRouter router = createTransitRouter(f.schedule, f.config, f.network);
		Coord toCoord = new Coord(28100, 5050);
		List<? extends PlanElement> legs = router.calcRoute(DefaultRoutingRequest.withoutAttributes(new FakeFacility(new Coord(24100, 50)), new FakeFacility(toCoord), 6.0 * 3600, null));
		assertEquals(5, legs.size());
		assertEquals(TransportMode.walk, ((Leg) legs.get(0)).getMode());
		assertEquals(TransportMode.pt, ((Leg) legs.get(1)).getMode());
		assertEquals(TransportMode.walk, ((Leg) legs.get(2)).getMode());
		assertEquals(TransportMode.pt, ((Leg) legs.get(3)).getMode());
		assertEquals(TransportMode.walk, ((Leg) legs.get(4)).getMode());
		assertTrue("expected TransitRoute in leg.", ((Leg) legs.get(1)).getRoute() instanceof TransitPassengerRoute);
		TransitPassengerRoute ptRoute = (TransitPassengerRoute) ((Leg) legs.get(1)).getRoute();
		assertEquals(Id.create("22", TransitStopFacility.class), ptRoute.getAccessStopId());
		assertEquals(Id.create("18", TransitStopFacility.class), ptRoute.getEgressStopId());
		assertEquals(f.greenLine.getId(), ptRoute.getLineId());
		assertEquals(Id.create("green clockwise", TransitRoute.class), ptRoute.getRouteId());
		assertTrue("expected TransitRoute in leg.", ((Leg) legs.get(3)).getRoute() instanceof TransitPassengerRoute);
		ptRoute = (TransitPassengerRoute) ((Leg) legs.get(3)).getRoute();
		assertEquals(Id.create("4", TransitStopFacility.class), ptRoute.getAccessStopId());
		assertEquals(Id.create("12", TransitStopFacility.class), ptRoute.getEgressStopId());
		assertEquals(f.blueLine.getId(), ptRoute.getLineId());
		assertEquals(Id.create("blue A > I", TransitRoute.class), ptRoute.getRouteId());
		double actualTravelTime = 0.0;
		for (PlanElement leg : legs) {
			actualTravelTime += ((Leg) leg).getTravelTime().seconds();
			System.out.println(((Leg) leg).getTravelTime().seconds());
		}
		double expectedTravelTime = 3060 + // agent takes the *:06 course, arriving in C at *:18, departing at *:21, arriving in K at*:31
				CoordUtils.calcEuclideanDistance(f.schedule.getFacilities().get(Id.create("21", TransitStopFacility.class)).getCoord(), toCoord) / raptorParams.getBeelineWalkSpeed();
		expectedTravelTime = 4250;
		assertEquals(Math.ceil(expectedTravelTime), actualTravelTime, MatsimTestUtils.EPSILON);
	}

	@Test
	public void testNoModeToModePenaltiesBusBusAndTrainBus() {
		Fixture f = new Fixture();
		f.init();
		ConfigUtils.addOrGetModule(f.config, SwissRailRaptorConfigGroup.class).addModeToModeTransferPenalty(new SwissRailRaptorConfigGroup.ModeToModeTransferPenalty("train", "bus", 1.3));
		ConfigUtils.addOrGetModule(f.config, SwissRailRaptorConfigGroup.class).addModeToModeTransferPenalty(new SwissRailRaptorConfigGroup.ModeToModeTransferPenalty("bus", "train", 1.3));
		ConfigUtils.addOrGetModule(f.config, SwissRailRaptorConfigGroup.class).addModeToModeTransferPenalty(new SwissRailRaptorConfigGroup.ModeToModeTransferPenalty("bus", "bus", 4.02));
		RaptorParameters raptorParams = RaptorUtils.createParameters(f.config);
		TransitRouter router = createTransitRouter(f.schedule, f.config, f.network);
		Coord toCoord = new Coord(28100, 5050);
		List<? extends PlanElement> legs = router.calcRoute(DefaultRoutingRequest.withoutAttributes(new FakeFacility(new Coord(24100, 50)), new FakeFacility(toCoord), 6.0 * 3600, null));
		assertEquals(5, legs.size());
		assertEquals(TransportMode.walk, ((Leg) legs.get(0)).getMode());
		assertEquals(TransportMode.pt, ((Leg) legs.get(1)).getMode());
		assertEquals(TransportMode.walk, ((Leg) legs.get(2)).getMode());
		assertEquals(TransportMode.pt, ((Leg) legs.get(3)).getMode());
		assertEquals(TransportMode.walk, ((Leg) legs.get(4)).getMode());
		assertTrue("expected TransitRoute in leg.", ((Leg) legs.get(1)).getRoute() instanceof TransitPassengerRoute);
		TransitPassengerRoute ptRoute = (TransitPassengerRoute) ((Leg) legs.get(1)).getRoute();
		assertEquals(Id.create("22", TransitStopFacility.class), ptRoute.getAccessStopId());
		assertEquals(Id.create("18", TransitStopFacility.class), ptRoute.getEgressStopId());
		assertEquals(f.greenLine.getId(), ptRoute.getLineId());
		assertEquals(Id.create("green clockwise", TransitRoute.class), ptRoute.getRouteId());
		assertTrue("expected TransitRoute in leg.", ((Leg) legs.get(3)).getRoute() instanceof TransitPassengerRoute);
		ptRoute = (TransitPassengerRoute) ((Leg) legs.get(3)).getRoute();
		assertEquals(Id.create("4", TransitStopFacility.class), ptRoute.getAccessStopId());
		assertEquals(Id.create("12", TransitStopFacility.class), ptRoute.getEgressStopId());
		assertEquals(f.redLine.getId(), ptRoute.getLineId());
		assertEquals(Id.create("red C > G", TransitRoute.class), ptRoute.getRouteId());
		double actualTravelTime = 0.0;
		for (PlanElement leg : legs) {
			actualTravelTime += ((Leg) leg).getTravelTime().seconds();
			System.out.println(((Leg) leg).getTravelTime().seconds());
		}
		double expectedTravelTime = 3060 + // agent takes the *:06 course, arriving in C at *:18, departing at *:21, arriving in K at*:31
				CoordUtils.calcEuclideanDistance(f.schedule.getFacilities().get(Id.create("21", TransitStopFacility.class)).getCoord(), toCoord) / raptorParams.getBeelineWalkSpeed();
		expectedTravelTime = 4310;
		assertEquals(Math.ceil(expectedTravelTime), actualTravelTime, MatsimTestUtils.EPSILON);
	}

	@Test
	public void testTravelTimeForSplitTrips() {
		Fixture f = new Fixture();
		f.init();
		ConfigUtils.addOrGetModule(f.config, SwissRailRaptorConfigGroup.class).addModeToModeTransferPenalty(new SwissRailRaptorConfigGroup.ModeToModeTransferPenalty("bus", "bus", 100));
		ConfigUtils.addOrGetModule(f.config, SwissRailRaptorConfigGroup.class).setTransferPenaltyCostPerTravelTimeHour(1.0);
		RaptorParameters raptorParams = RaptorUtils.createParameters(f.config);
		TransitRouter router = createTransitRouter(f.schedule, f.config, f.network);
		Coord toCoord = new Coord(24100, 50);
		List<? extends PlanElement> legs = router.calcRoute(DefaultRoutingRequest.withoutAttributes(new FakeFacility(new Coord(16100, 50)), new FakeFacility(toCoord), 6.0 * 3600, null));
		assertEquals(7, legs.size());
		assertEquals(TransportMode.walk, ((Leg) legs.get(0)).getMode());
		assertEquals(TransportMode.pt, ((Leg) legs.get(1)).getMode());
		assertEquals(TransportMode.walk, ((Leg) legs.get(2)).getMode());
		assertEquals(TransportMode.pt, ((Leg) legs.get(3)).getMode());
		assertEquals(TransportMode.walk, ((Leg) legs.get(4)).getMode());
		assertTrue("expected TransitRoute in leg.", ((Leg) legs.get(1)).getRoute() instanceof TransitPassengerRoute);
		TransitPassengerRoute ptRoute = (TransitPassengerRoute) ((Leg) legs.get(1)).getRoute();
		assertEquals(Id.create("23", TransitStopFacility.class), ptRoute.getAccessStopId());
		assertEquals(Id.create("18", TransitStopFacility.class), ptRoute.getEgressStopId());
		assertEquals(f.greenLine.getId(), ptRoute.getLineId());
		assertEquals(Id.create("green clockwise", TransitRoute.class), ptRoute.getRouteId());
		assertTrue("expected TransitRoute in leg.", ((Leg) legs.get(3)).getRoute() instanceof TransitPassengerRoute);
		ptRoute = (TransitPassengerRoute) ((Leg) legs.get(3)).getRoute();
		assertEquals(Id.create("4", TransitStopFacility.class), ptRoute.getAccessStopId());
		assertEquals(Id.create("12", TransitStopFacility.class), ptRoute.getEgressStopId());
		assertEquals(f.blueLine.getId(), ptRoute.getLineId());
		assertEquals(Id.create("blue A > I", TransitRoute.class), ptRoute.getRouteId());
		assertTrue("expected TransitRoute in leg.", ((Leg) legs.get(5)).getRoute() instanceof TransitPassengerRoute);
		ptRoute = (TransitPassengerRoute) ((Leg) legs.get(5)).getRoute();
		assertEquals(Id.create("21", TransitStopFacility.class), ptRoute.getAccessStopId());
		assertEquals(Id.create("22", TransitStopFacility.class), ptRoute.getEgressStopId());
		assertEquals(f.greenLine.getId(), ptRoute.getLineId());
		assertEquals(Id.create("green clockwise", TransitRoute.class), ptRoute.getRouteId());
		double actualTravelTime = 0.0;
		for (PlanElement leg : legs) {
			actualTravelTime += ((Leg) leg).getTravelTime().seconds();
			System.out.println(((Leg) leg).getTravelTime().seconds());
		}
		double expectedTravelTime = 3060 + // agent takes the *:06 course, arriving in C at *:18, departing at *:21, arriving in K at*:31
				CoordUtils.calcEuclideanDistance(f.schedule.getFacilities().get(Id.create("21", TransitStopFacility.class)).getCoord(), toCoord) / raptorParams.getBeelineWalkSpeed();
		expectedTravelTime = 3827;
		assertEquals(Math.ceil(expectedTravelTime), actualTravelTime, MatsimTestUtils.EPSILON);
	}
}