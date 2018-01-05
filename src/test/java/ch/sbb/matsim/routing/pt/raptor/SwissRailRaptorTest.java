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
import org.matsim.pt.routes.ExperimentalTransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.testcases.MatsimTestCase;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
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
}