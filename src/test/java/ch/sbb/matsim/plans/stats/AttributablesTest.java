package ch.sbb.matsim.plans.stats;

import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.routes.GenericRouteImpl;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Attributables} (mainly the {@code TripAttributable}).
 */
public class AttributablesTest {

    private static final double EPS = 1e-9;

    private PopulationFactory pf() {
        return ScenarioUtils.createScenario(ConfigUtils.createConfig()).getPopulation().getFactory();
    }

    /** Build a person with a single trip: origin -> leg -> destination. */
    private TripStructureUtils.Trip buildTrip(PopulationFactory pf, Person person,
                                              String originType, String destType,
                                              String mode, double distance) {
        Plan plan = pf.createPlan();
        Activity origin = pf.createActivityFromCoord(originType, new Coord(0, 0));
        origin.setEndTime(8 * 3600);
        Leg leg = pf.createLeg(mode);
        if (distance > 0) {
            GenericRouteImpl r = new GenericRouteImpl(null, null);
            r.setDistance(distance);
            leg.setRoute(r);
        }
        Activity dest = pf.createActivityFromCoord(destType, new Coord(1000, 0));
        plan.addActivity(origin);
        plan.addLeg(leg);
        plan.addActivity(dest);
        person.addPlan(plan);
        return TripStructureUtils.getTrips(plan).get(0);
    }

    @Test
    public void carTripIsClassifiedAsMIV() {
        PopulationFactory pf = pf();
        Person p = pf.createPerson(Id.createPersonId("p1"));
        TripStructureUtils.Trip trip = buildTrip(pf, p, "home", "work", "car", 12345.0);

        Attributables.AttributableProvider.TripAttributable t =
                new Attributables.AttributableProvider.TripAttributable(trip, p);

        assertEquals("car", t.getAttributes().getAttribute("mode"));
        assertEquals(1.0, ((Number) t.getAttributes().getAttribute("modeMIV")).doubleValue(), EPS);
        assertEquals(0.0, ((Number) t.getAttributes().getAttribute("modePt")).doubleValue(), EPS);
        assertEquals(0.0, ((Number) t.getAttributes().getAttribute("modeLV")).doubleValue(), EPS);
        assertEquals(12345.0, ((Number) t.getAttributes().getAttribute("distance")).doubleValue(), EPS);
    }

    @Test
    public void rideAndAvtaxiAreMIV() {
        PopulationFactory pf = pf();
        for (String mode : List.of("ride", "avtaxi")) {
            Person p = pf.createPerson(Id.createPersonId("p_" + mode));
            TripStructureUtils.Trip trip = buildTrip(pf, p, "home", "leisure", mode, 100.0);
            Attributables.AttributableProvider.TripAttributable t =
                    new Attributables.AttributableProvider.TripAttributable(trip, p);
            assertEquals(1.0, ((Number) t.getAttributes().getAttribute("modeMIV")).doubleValue(), EPS, mode);
            assertEquals(0.0, ((Number) t.getAttributes().getAttribute("modePt")).doubleValue(), EPS, mode);
            assertEquals(0.0, ((Number) t.getAttributes().getAttribute("modeLV")).doubleValue(), EPS, mode);
        }
    }

    @Test
    public void ptTripIsPt() {
        PopulationFactory pf = pf();
        Person p = pf.createPerson(Id.createPersonId("p2"));
        TripStructureUtils.Trip trip = buildTrip(pf, p, "home", "work", "pt", 5000.0);
        Attributables.AttributableProvider.TripAttributable t =
                new Attributables.AttributableProvider.TripAttributable(trip, p);

        assertEquals(0.0, ((Number) t.getAttributes().getAttribute("modeMIV")).doubleValue(), EPS);
        assertEquals(1.0, ((Number) t.getAttributes().getAttribute("modePt")).doubleValue(), EPS);
        assertEquals(0.0, ((Number) t.getAttributes().getAttribute("modeLV")).doubleValue(), EPS);
    }

    @Test
    public void walkAndBikeAreLV() {
        PopulationFactory pf = pf();
        for (String mode : List.of("walk", "bike", "walk_main")) {
            Person p = pf.createPerson(Id.createPersonId("p_" + mode));
            TripStructureUtils.Trip trip = buildTrip(pf, p, "home", "shopping", mode, 800.0);
            Attributables.AttributableProvider.TripAttributable t =
                    new Attributables.AttributableProvider.TripAttributable(trip, p);
            assertEquals(0.0, ((Number) t.getAttributes().getAttribute("modeMIV")).doubleValue(), EPS, mode);
            assertEquals(0.0, ((Number) t.getAttributes().getAttribute("modePt")).doubleValue(), EPS, mode);
            assertEquals(1.0, ((Number) t.getAttributes().getAttribute("modeLV")).doubleValue(), EPS, mode);
        }
    }

    @Test
    public void weightDefaultsToOne() {
        PopulationFactory pf = pf();
        Person p = pf.createPerson(Id.createPersonId("p3"));
        TripStructureUtils.Trip trip = buildTrip(pf, p, "home", "work", "car", 100.0);
        Attributables.AttributableProvider.TripAttributable t =
                new Attributables.AttributableProvider.TripAttributable(trip, p);
        Object weight = t.getAttributes().getAttribute("weight");
        assertNotNull(weight);
        assertEquals(1, ((Number) weight).intValue());
    }

    @Test
    public void distanceFromCarNetDistanceLegAttributeIsPreferred() {
        PopulationFactory pf = pf();
        Person p = pf.createPerson(Id.createPersonId("p4"));

        Plan plan = pf.createPlan();
        Activity a1 = pf.createActivityFromCoord("home", new Coord(0, 0));
        a1.setEndTime(8 * 3600);
        Leg leg = pf.createLeg("car");
        // route distance differs from car_net_distance attribute; the latter must win.
        GenericRouteImpl r = new GenericRouteImpl(null, null);
        r.setDistance(123.0);
        leg.setRoute(r);
        leg.getAttributes().putAttribute("car_net_distance", 9999.0);
        Activity a2 = pf.createActivityFromCoord("work", new Coord(1000, 0));
        plan.addActivity(a1);
        plan.addLeg(leg);
        plan.addActivity(a2);
        p.addPlan(plan);

        TripStructureUtils.Trip trip = TripStructureUtils.getTrips(plan).get(0);
        Attributables.AttributableProvider.TripAttributable t =
                new Attributables.AttributableProvider.TripAttributable(trip, p);

        assertEquals(9999.0, ((Number) t.getAttributes().getAttribute("distance")).doubleValue(), EPS);
    }

    @Test
    public void originAndDestinationActivitiesAreExposed() {
        PopulationFactory pf = pf();
        Person p = pf.createPerson(Id.createPersonId("p5"));
        TripStructureUtils.Trip trip = buildTrip(pf, p, "home", "work", "car", 1.0);
        Attributables.AttributableProvider.TripAttributable t =
                new Attributables.AttributableProvider.TripAttributable(trip, p);

        assertEquals("home", t.getOriginActivity().getType());
        assertEquals("work", t.getDestinationActivity().getType());
    }

    @Test
    public void addLookupAttributesPropagatesIntoAttributes() {
        PopulationFactory pf = pf();
        Person p = pf.createPerson(Id.createPersonId("p6"));
        TripStructureUtils.Trip trip = buildTrip(pf, p, "home", "work", "car", 1.0);
        Attributables.AttributableProvider.TripAttributable t =
                new Attributables.AttributableProvider.TripAttributable(trip, p);

        t.addLookupAttributes(java.util.Map.of("person_zone_sl3_id", 2));
        assertEquals(2, t.getAttributes().getAttribute("person_zone_sl3_id"));
    }
}

