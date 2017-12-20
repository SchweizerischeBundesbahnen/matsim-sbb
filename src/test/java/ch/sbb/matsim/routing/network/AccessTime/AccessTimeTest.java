package ch.sbb.matsim.routing.network.AccessTime;

import java.util.ArrayList;

import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.api.experimental.events.TeleportationArrivalEvent;


public class AccessTimeTest {

    private Coord bern = new Coord(600000, 200000); // 20
    private Coord stleo = new Coord(598345.54, 122581.99); // 2
    private Coord zurich = new Coord(682304, 248065); // 2
    private Coord thun = new Coord(613843.82, 178094.54); // 3
    private Coord lausanne = new Coord(613843.82, 178094.54); // 11


    @Test
    public final void testBike() {
        TestFixture fixture = new TestFixture(bern, stleo, "bike", true, -1.0);
        fixture.run();

        Person person = fixture.population.getPersons().get(Id.createPersonId("1"));
        for (PlanElement pe : person.getSelectedPlan().getPlanElements()) {
            if (pe instanceof Leg) {
                Leg leg = (Leg) pe;
                System.out.println(leg.getTravelTime());
            }
        }

        Leg accessLeg = (Leg) person.getSelectedPlan().getPlanElements().get(1);
        Leg egressLeg = (Leg) person.getSelectedPlan().getPlanElements().get(5);

        Assert.assertEquals(40.0, accessLeg.getTravelTime(), 1e-10);
        Assert.assertEquals(4.0, egressLeg.getTravelTime(), 1e-10);
    }


    private double getScoring(Boolean withAccessTime, double constant, double utilityOfTravaleling) {
        TestFixture fixture = new TestFixture(bern, stleo, "car", withAccessTime, constant);

        fixture.egressParams.setMarginalUtilityOfTraveling(utilityOfTravaleling);
        fixture.accessParams.setMarginalUtilityOfTraveling(utilityOfTravaleling);
        fixture.run();


        Person person = fixture.population.getPersons().get(Id.createPersonId("1"));
        return person.getSelectedPlan().getScore();

    }

    @Test
    public final void testCarEgressNull() {

        ArrayList<Double> scores = new ArrayList<>();
        Double score = 0.0;

        score = getScoring(false, 0.0, -1.68);
        Assert.assertEquals(-432.0, score, 0.001);
        scores.add(score);

        scores.add(getScoring(true, 0.0, -1.68));
        scores.add(getScoring(true, -1.0, -1.68));
        scores.add(getScoring(true, -10.0, -1.68));
        scores.add(getScoring(true, -10.0, -30));

        System.out.println(scores.toString());

        ArrayList<Double> expectedScores = new ArrayList<>();
        expectedScores.add(-432.0);
        expectedScores.add(-432.009);
        expectedScores.add(-433.009);
        expectedScores.add(-442.009);
        expectedScores.add(-442.016);


    }


    @Test
    public final void testCar() {

        TestFixture fixture = new TestFixture(bern, stleo, "car", true, -1.0);
        fixture.run();

        Person person = fixture.population.getPersons().get(Id.createPersonId("1"));
        for (PlanElement pe : person.getSelectedPlan().getPlanElements()) {
            if (pe instanceof Leg) {
                Leg leg = (Leg) pe;
                System.out.println(leg.getTravelTime());
            }
        }

        Leg accessLeg = (Leg) person.getSelectedPlan().getPlanElements().get(1);
        Leg egressLeg = (Leg) person.getSelectedPlan().getPlanElements().get(5);

        Assert.assertEquals(20.0, accessLeg.getTravelTime(), 1e-10);
        Assert.assertEquals(2.0, egressLeg.getTravelTime(), 1e-10);


        assertEqualEvent(PersonDepartureEvent.class, 21600, fixture.allEvents.get(1));
        assertEqualEvent(TeleportationArrivalEvent.class, 21620, fixture.allEvents.get(2));

    }

    private static void assertEqualEvent(Class<? extends Event> eventClass, double time, Event event) {
        Assert.assertTrue(event.getClass().isAssignableFrom(event.getClass()));
        Assert.assertEquals(time, event.getTime(), 1e-7);
    }

}
