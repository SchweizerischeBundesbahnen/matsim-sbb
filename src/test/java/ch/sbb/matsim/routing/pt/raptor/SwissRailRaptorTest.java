/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.routing.pt.raptor;

import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.router.FakeFacility;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.router.TransitRouterConfig;
import org.matsim.pt.routes.ExperimentalTransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.testcases.MatsimTestCase;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Most of these tests were copied from org.matsim.pt.router.TransitRouterImplTest
 * and only minimally adapted to make them run with SwissRailRaptor.
 *
 * @author mrieser / SBB
 */
public class SwissRailRaptorTest {

    private TransitRouter createTransitRouter(TransitSchedule schedule, RaptorConfig raptorConfig) {
        SwissRailRaptorData data = SwissRailRaptorData.create(schedule, raptorConfig);
        SwissRailRaptor raptor = new SwissRailRaptor(data);
        return raptor;
    }

    @Test
    public void testSingleLine() {
        Fixture f = new Fixture();
        f.init();
        RaptorConfig raptorConfig = RaptorUtils.createRaptorConfig(f.config);
        TransitRouter router = createTransitRouter(f.schedule, raptorConfig);
        Coord fromCoord = new Coord(3800, 5100);
        Coord toCoord = new Coord(16100, 5050);
        List<Leg> legs = router.calcRoute(new FakeFacility(fromCoord), new FakeFacility(toCoord), 5.0*3600, null);
        assertEquals(3, legs.size());
        assertEquals(TransportMode.access_walk, legs.get(0).getMode());
        assertEquals(TransportMode.pt, legs.get(1).getMode());
        assertEquals(TransportMode.egress_walk, legs.get(2).getMode());
        assertTrue("expected TransitRoute in leg.", legs.get(1).getRoute() instanceof ExperimentalTransitRoute);
        ExperimentalTransitRoute ptRoute = (ExperimentalTransitRoute) legs.get(1).getRoute();
        assertEquals(Id.create("0", TransitStopFacility.class), ptRoute.getAccessStopId());
        assertEquals(Id.create("6", TransitStopFacility.class), ptRoute.getEgressStopId());
        assertEquals(f.blueLine.getId(), ptRoute.getLineId());
        assertEquals(Id.create("blue A > I", TransitRoute.class), ptRoute.getRouteId());
        double actualTravelTime = 0.0;
        for (Leg leg : legs) {
            actualTravelTime += leg.getTravelTime();
        }
        double expectedTravelTime = 29.0 * 60 + // agent takes the *:06 course, arriving in D at *:29
                CoordUtils.calcEuclideanDistance(f.schedule.getFacilities().get(Id.create("6", TransitStopFacility.class)).getCoord(), toCoord) / raptorConfig.getBeelineWalkSpeed();
        assertEquals(expectedTravelTime, actualTravelTime, MatsimTestCase.EPSILON);
    }

    @Test
    public void testFromToSameStop() {
        Fixture f = new Fixture();
        f.init();
        RaptorConfig raptorConfig = RaptorUtils.createRaptorConfig(f.config);
        TransitRouter router = createTransitRouter(f.schedule, raptorConfig);
        Coord fromCoord = new Coord((double) 3800, (double) 5100);
        Coord toCoord = new Coord((double) 4100, (double) 5050);
        List<Leg> legs = router.calcRoute(new FakeFacility(fromCoord), new FakeFacility(toCoord), 5.0*3600, null);
        assertEquals(1, legs.size());
        assertEquals(TransportMode.transit_walk, legs.get(0).getMode());
        double actualTravelTime = 0.0;
        for (Leg leg : legs) {
            actualTravelTime += leg.getTravelTime();
        }
        double expectedTravelTime = CoordUtils.calcEuclideanDistance(fromCoord, toCoord) / raptorConfig.getBeelineWalkSpeed();
        assertEquals(expectedTravelTime, actualTravelTime, MatsimTestCase.EPSILON);
    }

    @Test
    public void testDirectWalkCheaper() {
        Fixture f = new Fixture();
        f.init();
        RaptorConfig raptorConfig = RaptorUtils.createRaptorConfig(f.config);
        TransitRouter router = createTransitRouter(f.schedule, raptorConfig);
        Coord fromCoord = new Coord((double) 4000, (double) 3000);
        Coord toCoord = new Coord((double) 8000, (double) 3000);
        List<Leg> legs = router.calcRoute(new FakeFacility(fromCoord), new FakeFacility(toCoord), 5.0*3600, null);
        assertEquals(1, legs.size());
        assertEquals(TransportMode.transit_walk, legs.get(0).getMode());
        double actualTravelTime = 0.0;
        for (Leg leg : legs) {
            actualTravelTime += leg.getTravelTime();
        }
        double expectedTravelTime = CoordUtils.calcEuclideanDistance(fromCoord, toCoord) / raptorConfig.getBeelineWalkSpeed();
        assertEquals(expectedTravelTime, actualTravelTime, MatsimTestCase.EPSILON);
    }


    @Test
    public void testSingleLine_DifferentWaitingTime() {
        Fixture f = new Fixture();
        f.init();
        RaptorConfig raptorConfig = RaptorUtils.createRaptorConfig(f.config);
        TransitRouter router = createTransitRouter(f.schedule, raptorConfig);
        Coord fromCoord = new Coord((double) 4000, (double) 5002);
        Coord toCoord = new Coord((double) 8000, (double) 5002);

        double inVehicleTime = 7.0*60; // travel time from A to B
        for (int min = 0; min < 30; min += 3) {
            List<Leg> legs = router.calcRoute(new FakeFacility(fromCoord), new FakeFacility(toCoord), 5.0*3600 + min*60, null);
            assertEquals(3, legs.size()); // walk-pt-walk
            double actualTravelTime = 0.0;
            for (Leg leg : legs) {
                actualTravelTime += leg.getTravelTime();
            }
            double waitingTime = ((46 - min) % 20) * 60; // departures at *:06 and *:26 and *:46
            assertEquals("expected different waiting time at 05:"+min, waitingTime, actualTravelTime - inVehicleTime, MatsimTestCase.EPSILON);
        }
    }

    @Test
    public void testLineChange() {
        Fixture f = new Fixture();
        f.init();
        RaptorConfig raptorConfig = RaptorUtils.createRaptorConfig(f.config);
        TransitRouter router = createTransitRouter(f.schedule, raptorConfig);
        Coord toCoord = new Coord(16100, 10050);
        List<Leg> legs = router.calcRoute(new FakeFacility(new Coord(3800, 5100)), new FakeFacility(toCoord), 6.0*3600, null);
        assertEquals(5, legs.size());
        assertEquals(TransportMode.access_walk, legs.get(0).getMode());
        assertEquals(TransportMode.pt, legs.get(1).getMode());
        assertEquals(TransportMode.transit_walk, legs.get(2).getMode());
        assertEquals(TransportMode.pt, legs.get(3).getMode());
        assertEquals(TransportMode.egress_walk, legs.get(4).getMode());
        assertTrue("expected TransitRoute in leg.", legs.get(1).getRoute() instanceof ExperimentalTransitRoute);
        ExperimentalTransitRoute ptRoute = (ExperimentalTransitRoute) legs.get(1).getRoute();
        assertEquals(Id.create("0", TransitStopFacility.class), ptRoute.getAccessStopId());
        assertEquals(Id.create("4", TransitStopFacility.class), ptRoute.getEgressStopId());
        assertEquals(f.blueLine.getId(), ptRoute.getLineId());
        assertEquals(Id.create("blue A > I", TransitRoute.class), ptRoute.getRouteId());
        assertTrue("expected TransitRoute in leg.", legs.get(3).getRoute() instanceof ExperimentalTransitRoute);
        ptRoute = (ExperimentalTransitRoute) legs.get(3).getRoute();
        assertEquals(Id.create("18", TransitStopFacility.class), ptRoute.getAccessStopId());
        assertEquals(Id.create("19", TransitStopFacility.class), ptRoute.getEgressStopId());
        assertEquals(f.greenLine.getId(), ptRoute.getLineId());
        assertEquals(Id.create("green clockwise", TransitRoute.class), ptRoute.getRouteId());
        double actualTravelTime = 0.0;
        for (Leg leg : legs) {
            actualTravelTime += leg.getTravelTime();
        }
        double expectedTravelTime = 31.0 * 60 + // agent takes the *:06 course, arriving in C at *:18, departing at *:21, arriving in K at*:31
                CoordUtils.calcEuclideanDistance(f.schedule.getFacilities().get(Id.create("19", TransitStopFacility.class)).getCoord(), toCoord) / raptorConfig.getBeelineWalkSpeed();
        assertEquals(expectedTravelTime, actualTravelTime, MatsimTestCase.EPSILON);
    }

    @Test
    public void testFasterAlternative() {
        /* idea: travel from A to G
         * One could just take the blue line and travel from A to G (dep *:46, arrival *:28),
         * or one could first travel from A to C (dep *:46, arr *:58), and then take the red line
         * from C to G (dep *:00, arr *:09), but this requires an additional transfer (but
         * at the same StopFacility, so there should not be a transit_walk-leg).
         */
        Fixture f = new Fixture();
        f.init();
        RaptorConfig raptorConfig = RaptorUtils.createRaptorConfig(f.config);
        TransitRouterConfig trConfig = new TransitRouterConfig(f.scenario.getConfig().planCalcScore(),
                f.scenario.getConfig().plansCalcRoute(), f.scenario.getConfig().transitRouter(),
                f.scenario.getConfig().vspExperimental());
        TransitRouter router = createTransitRouter(f.schedule, raptorConfig);
        Coord toCoord = new Coord(28100, 4950);
        List<Leg> legs = router.calcRoute(new FakeFacility( new Coord(3800, 5100)), new FakeFacility(toCoord), 5.0*3600 + 40.0*60, null);
        assertEquals("wrong number of legs", 4, legs.size());
        assertEquals(TransportMode.access_walk, legs.get(0).getMode());
        assertEquals(TransportMode.pt, legs.get(1).getMode());
        assertEquals(TransportMode.pt, legs.get(2).getMode());
        assertEquals(TransportMode.egress_walk, legs.get(3).getMode());
        assertTrue("expected TransitRoute in leg.", legs.get(1).getRoute() instanceof ExperimentalTransitRoute);
        ExperimentalTransitRoute ptRoute = (ExperimentalTransitRoute) legs.get(1).getRoute();
        assertEquals(Id.create("0", TransitStopFacility.class), ptRoute.getAccessStopId());
        assertEquals(Id.create("4", TransitStopFacility.class), ptRoute.getEgressStopId());
        assertEquals(f.blueLine.getId(), ptRoute.getLineId());
        assertEquals(Id.create("blue A > I", TransitRoute.class), ptRoute.getRouteId());
        assertTrue("expected TransitRoute in leg.", legs.get(2).getRoute() instanceof ExperimentalTransitRoute);
        ptRoute = (ExperimentalTransitRoute) legs.get(2).getRoute();
        assertEquals(Id.create("4", TransitStopFacility.class), ptRoute.getAccessStopId());
        assertEquals(Id.create("12", TransitStopFacility.class), ptRoute.getEgressStopId());
        assertEquals(f.redLine.getId(), ptRoute.getLineId());
        assertEquals(Id.create("red C > G", TransitRoute.class), ptRoute.getRouteId());
        double actualTravelTime = 0.0;
        for (Leg leg : legs) {
            actualTravelTime += leg.getTravelTime();
        }
        double expectedTravelTime = 29.0 * 60 + // agent takes the *:46 course, arriving in C at *:58, departing at *:00, arriving in G at*:09
                CoordUtils.calcEuclideanDistance(f.schedule.getFacilities().get(Id.create("12", TransitStopFacility.class)).getCoord(), toCoord) / trConfig.getBeelineWalkSpeed();
        assertEquals(expectedTravelTime, actualTravelTime, MatsimTestCase.EPSILON);
    }


    @Test
    public void testTransferWeights() {
        /* idea: travel from C to F
         * If starting at the right time, one could take the red line to G and travel back with blue to F.
         * If one doesn't want to switch lines, one could take the blue line from C to F directly.
         * Using the red line (dep *:00, change at G *:09/*:12) results in an arrival time of *:19,
         * using the blue line only (dep *:02) results in an arrival time of *:23. So the line switch
         * cost must be larger than 4 minutes to have an effect.
         */
        Fixture f = new Fixture();
        f.init();
        RaptorConfig raptorConfig = RaptorUtils.createRaptorConfig(f.config);
        raptorConfig.setTransferPenaltyCost(0);
        TransitRouter router = createTransitRouter(f.schedule, raptorConfig);
        List<Leg> legs = router.calcRoute(new FakeFacility(new Coord((double) 11900, (double) 5100)), new FakeFacility(new Coord((double) 24100, (double) 4950)), 6.0*3600 - 5.0*60, null);
        assertEquals("wrong number of legs", 5, legs.size());
        assertEquals(TransportMode.access_walk, legs.get(0).getMode());
        assertEquals(TransportMode.pt, legs.get(1).getMode());
        assertEquals(f.redLine.getId(), ((ExperimentalTransitRoute) legs.get(1).getRoute()).getLineId());
        assertEquals(TransportMode.transit_walk, legs.get(2).getMode());
        assertEquals(TransportMode.pt, legs.get(3).getMode());
        assertEquals(f.blueLine.getId(), ((ExperimentalTransitRoute) legs.get(3).getRoute()).getLineId());
        assertEquals(TransportMode.egress_walk, legs.get(4).getMode());

        raptorConfig.setTransferPenaltyCost(-300.0 * raptorConfig.getMarginalUtilityOfTravelTimePt_utl_s()); // corresponds to 5 minutes transit travel time
        legs = router.calcRoute(new FakeFacility(new Coord((double) 11900, (double) 5100)), new FakeFacility(new Coord((double) 24100, (double) 4950)), 6.0*3600 - 5.0*60, null);
        assertEquals(3, legs.size());
        assertEquals(TransportMode.access_walk, legs.get(0).getMode());
        assertEquals(TransportMode.pt, legs.get(1).getMode());
        assertEquals(f.blueLine.getId(), ((ExperimentalTransitRoute) legs.get(1).getRoute()).getLineId());
        assertEquals(TransportMode.egress_walk, legs.get(2).getMode());
    }

    @Test
    public void testTransferTime() {
        /* idea: travel from C to F
         * If starting at the right time, one could take the red line to G and travel back with blue to F.
         * If one doesn't want to switch lines, one could take the blue line from C to F directly.
         * Using the red line (dep *:00, change at G *:09/*:12) results in an arrival time of *:19,
         * using the blue line only (dep *:02) results in an arrival time of *:23.
         * For the line switch at G, 3 minutes are available. If the minimum transfer time is larger than
         * that, the direct connection should be taken.
         */
        Fixture f = new Fixture();
        f.init();
        RaptorConfig raptorConfig = RaptorUtils.createRaptorConfig(f.config);
        raptorConfig.setTransferPenaltyCost(0);
        assertEquals(0, raptorConfig.getMinimalTransferTime(), 1e-8);
        TransitRouter router = createTransitRouter(f.schedule, raptorConfig);
        List<Leg> legs = router.calcRoute(new FakeFacility(new Coord((double) 11900, (double) 5100)), new FakeFacility(new Coord((double) 24100, (double) 4950)), 6.0*3600 - 5.0*60, null);
        assertEquals("wrong number of legs",5, legs.size());
        assertEquals(TransportMode.access_walk, legs.get(0).getMode());
        assertEquals(TransportMode.pt, legs.get(1).getMode());
        assertEquals(f.redLine.getId(), ((ExperimentalTransitRoute) legs.get(1).getRoute()).getLineId());
        assertEquals(TransportMode.transit_walk, legs.get(2).getMode());
        assertEquals(TransportMode.pt, legs.get(3).getMode());
        assertEquals(f.blueLine.getId(), ((ExperimentalTransitRoute) legs.get(3).getRoute()).getLineId());
        assertEquals(TransportMode.egress_walk, legs.get(4).getMode());

        raptorConfig.setMinimalTransferTime(3*60+1); // just a little bit more than 3 minutes, so we miss the connection at G
        router = createTransitRouter(f.schedule, raptorConfig); // this is necessary to update the router for any change in config.
        legs = router.calcRoute(new FakeFacility(new Coord((double) 11900, (double) 5100)), new FakeFacility(new Coord((double) 24100, (double) 4950)), 6.0*3600 - 5.0*60, null);
        assertEquals("wrong number of legs",3, legs.size());
        assertEquals(TransportMode.access_walk, legs.get(0).getMode());
        assertEquals(TransportMode.pt, legs.get(1).getMode());
        assertEquals(f.blueLine.getId(), ((ExperimentalTransitRoute) legs.get(1).getRoute()).getLineId());
        assertEquals(TransportMode.egress_walk, legs.get(2).getMode());
    }

}