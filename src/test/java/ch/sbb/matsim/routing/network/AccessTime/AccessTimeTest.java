package ch.sbb.matsim.routing.network.AccessTime;

import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;

public class AccessTimeTest {

	public final Coord bern = new Coord(600000, 200000); // 20 Minutes access time
	public final Coord stleo = new Coord(598345.54, 122581.99); // 2 Minutes access time

	private void assertScoring(double constant, double utilityOfTravaleling, double expectedScore, String mode, String modesWithAcess) {
		TestFixture fixture = new TestFixture(bern, stleo, mode, true, constant, modesWithAcess);

		fixture.egressParams.setMarginalUtilityOfTraveling(utilityOfTravaleling);
		fixture.accessParams.setMarginalUtilityOfTraveling(utilityOfTravaleling);

		fixture.run();

		Person person = fixture.population.getPersons().get(Id.createPersonId("1"));
		Double score = person.getSelectedPlan().getScore();
		Assert.assertEquals(expectedScore, score, 0.001);

	}

	@Test
	public final void testScoringCarAccessForCarConstantNullUtilityNull() {
		assertScoring(0.0, 0.0, -2.59, "car", "car");
	}

	@Test
	public final void testScoringRideAccessForCarConstantNull() {
		assertScoring(0.0, -1.68, -2.59, "car", "car");
	}

	@Test
	public final void testScoringCarAccessForCar() {
		assertScoring(-1.0, -1.68, -6.590, "car", "car");
	}

	@Test
	public final void testScoringRideAccessForCarHigherConstant() {
		assertScoring(-10.0, -1.68, -42.59, "car", "car");
	}

	@Test
	public final void testScoringRideAccessForCarHigherConstantHigherUtility() {
		assertScoring(-10.0, -30, -42.59, "car", "car");
	}

	@Test
	public final void testScoringRideIfUsingAccess() {
		assertScoring(0.0, -30, -1.935, "ride", "car,ride");
	}

}
