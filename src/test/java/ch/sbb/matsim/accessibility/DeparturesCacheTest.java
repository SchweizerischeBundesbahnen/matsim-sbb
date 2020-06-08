package ch.sbb.matsim.accessibility;

import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitRouteStop;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleFactory;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class DeparturesCacheTest {

    @Test
    public void testGetNextDepartureTime() {
        Fixture f = new Fixture();

        DeparturesCache cache = new DeparturesCache(f.scenario.getTransitSchedule());

        Id<TransitStopFacility> stopId = Id.create("1", TransitStopFacility.class);
        Assert.assertEquals(7 * 3600, cache.getNextDepartureTime(stopId, 6 * 3600).seconds(), 1e-8);
        Assert.assertEquals(7 * 3600, cache.getNextDepartureTime(stopId, 7 * 3600).seconds(), 1e-8);
        Assert.assertEquals(7 * 3600 + 20 * 60, cache.getNextDepartureTime(stopId, 7 * 3600 + 1).seconds(), 1e-8);
        Assert.assertTrue(cache.getNextDepartureTime(stopId, 7 * 3600 + 40 * 60 + 1).isUndefined());

        stopId = Id.create("2", TransitStopFacility.class);
        Assert.assertEquals(7 * 3600 + 300, cache.getNextDepartureTime(stopId, 7 * 3600).seconds(), 1e-8); // line 1, route A
        Assert.assertEquals(7 * 3600 + 360, cache.getNextDepartureTime(stopId, 7 * 3600 + 301).seconds(), 1e-8); // line 2, route B
        Assert.assertEquals(7 * 3600 + 10 * 60 + 420, cache.getNextDepartureTime(stopId, 7 * 3600 + 361).seconds(), 1e-8); // line 2, route C
        Assert.assertEquals(7 * 3600 + 20 * 60 + 300, cache.getNextDepartureTime(stopId, 7 * 3600 + 1021).seconds(), 1e-8); // line 1, route A
    }

    private static class Fixture {

        Config config = ConfigUtils.createConfig();
        Scenario scenario = ScenarioUtils.createScenario(config);

        public Fixture() {
            TransitSchedule schedule = scenario.getTransitSchedule();
            TransitScheduleFactory f = schedule.getFactory();

            TransitStopFacility stop1 = f.createTransitStopFacility(Id.create("1", TransitStopFacility.class), new Coord(0, 0), false);
            TransitStopFacility stop2 = f.createTransitStopFacility(Id.create("2", TransitStopFacility.class), new Coord(500, 500), false);
            TransitStopFacility stop3 = f.createTransitStopFacility(Id.create("3", TransitStopFacility.class), new Coord(1000, 1000), false);
            TransitStopFacility stop4 = f.createTransitStopFacility(Id.create("4", TransitStopFacility.class), new Coord(0, 1000), false);
            TransitStopFacility stop5 = f.createTransitStopFacility(Id.create("5", TransitStopFacility.class), new Coord(1000, 0), false);

            schedule.addStopFacility(stop1);
            schedule.addStopFacility(stop2);
            schedule.addStopFacility(stop3);
            schedule.addStopFacility(stop4);
            schedule.addStopFacility(stop5);

            TransitLine line1 = f.createTransitLine(Id.create("1", TransitLine.class));
            List<TransitRouteStop> stops = new ArrayList<>();
            stops.add(f.createTransitRouteStopBuilder(stop1).departureOffset(0).build());
            stops.add(f.createTransitRouteStop(stop2, 240, 300));
            stops.add(f.createTransitRouteStopBuilder(stop3).arrivalOffset(600).build());
            TransitRoute route1A = f.createTransitRoute(Id.create("A", TransitRoute.class), null, stops, "train");
            route1A.addDeparture(f.createDeparture(Id.create("A1", Departure.class), 7 * 3600));
            route1A.addDeparture(f.createDeparture(Id.create("A2", Departure.class), 7 * 3600 + 20 * 60));
            route1A.addDeparture(f.createDeparture(Id.create("A3", Departure.class), 7 * 3600 + 40 * 60));
            line1.addRoute(route1A);
            schedule.addTransitLine(line1);

            TransitLine line2 = f.createTransitLine(Id.create("2", TransitLine.class));
            stops = new ArrayList<>();
            stops.add(f.createTransitRouteStopBuilder(stop4).departureOffset(0).build());
            stops.add(f.createTransitRouteStop(stop2, 300, 360));
            stops.add(f.createTransitRouteStopBuilder(stop5).arrivalOffset(720).build());
            TransitRoute route2B = f.createTransitRoute(Id.create("B", TransitRoute.class), null, stops, "train");
            route2B.addDeparture(f.createDeparture(Id.create("B1", Departure.class), 7 * 3600));
            route2B.addDeparture(f.createDeparture(Id.create("B2", Departure.class), 7 * 3600 + 20 * 60));
            route2B.addDeparture(f.createDeparture(Id.create("B3", Departure.class), 7 * 3600 + 40 * 60));
            line2.addRoute(route2B);
            stops = new ArrayList<>();
            stops.add(f.createTransitRouteStopBuilder(stop4).departureOffset(0).build());
            stops.add(f.createTransitRouteStop(stop2, 360, 420));
            stops.add(f.createTransitRouteStopBuilder(stop5).arrivalOffset(780).build());
            TransitRoute route2C = f.createTransitRoute(Id.create("C", TransitRoute.class), null, stops, "train");
            route2C.addDeparture(f.createDeparture(Id.create("C1", Departure.class), 7 * 3600 + 10 * 60));
            route2C.addDeparture(f.createDeparture(Id.create("C2", Departure.class), 7 * 3600 + 30 * 60));
            route2C.addDeparture(f.createDeparture(Id.create("C3", Departure.class), 7 * 3600 + 50 * 60));
            line2.addRoute(route2C);
            schedule.addTransitLine(line2);
        }
    }

}