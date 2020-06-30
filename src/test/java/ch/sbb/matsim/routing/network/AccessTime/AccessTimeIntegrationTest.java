package ch.sbb.matsim.routing.network.AccessTime;

import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.events.PersonDepartureEvent;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.TeleportationArrivalEvent;

public class AccessTimeIntegrationTest {

    public Coord bern = new Coord(600000, 200000); // 20
    public Coord stleo = new Coord(598345.54, 122581.99); // 2


    @Test
    public final void testIntegration() {

		TestFixture fixture = new TestFixture(bern, stleo, "car", true, -1.0, "car,bike");
		fixture.run();

		Person person = fixture.population.getPersons().get(Id.createPersonId("1"));

		Leg accessLeg = (Leg) person.getSelectedPlan().getPlanElements().get(1);
		Leg egressLeg = (Leg) person.getSelectedPlan().getPlanElements().get(5);

		Assert.assertEquals(20.0, accessLeg.getTravelTime().seconds(), 1e-10);
		Assert.assertEquals(2.0, egressLeg.getTravelTime().seconds(), 1e-10);

		assertEqualEvent(PersonDepartureEvent.class, 21600, fixture.allEvents.get(1));
		assertEqualEvent(TeleportationArrivalEvent.class, 21620, fixture.allEvents.get(2));
	}

    private static void assertEqualEvent(Class<? extends Event> eventClass, double time, Event event) {
        Assert.assertTrue(event.getClass().isAssignableFrom(event.getClass()));
        Assert.assertEquals(time, event.getTime(), 1e-7);
    }

}
