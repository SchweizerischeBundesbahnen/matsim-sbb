package ch.sbb.matsim.utils;

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.testcases.MatsimTestUtils;

public class SBBPersonUtilsTest {

	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils();

	@Test
	public void testNoActivity() {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		PopulationFactory pf = scenario.getPopulation().getFactory();

		Person person = pf.createPerson(Id.create(1, Person.class));
		Plan plan = pf.createPlan();

		person.addPlan(plan);

		Assert.assertNull(SBBPersonUtils.getHomeActivity(person));
	}

	@Test
	public void testHomeActivity() {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		PopulationFactory pf = scenario.getPopulation().getFactory();

		Person person = pf.createPerson(Id.create(1, Person.class));
		Plan plan = pf.createPlan();

		Activity act1 = pf.createActivityFromCoord("home", new Coord(1.0, 1.0));
		Leg leg = pf.createLeg("car");
		Activity act2 = pf.createActivityFromCoord("work", new Coord(10.0, 1.0));

		plan.addActivity(act1);
		plan.addLeg(leg);
		plan.addActivity(act2);

		person.addPlan(plan);

		Assert.assertEquals(SBBPersonUtils.getHomeActivity(person), act1);
	}

	@Test
	public void testExogenActivity() {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		PopulationFactory pf = scenario.getPopulation().getFactory();

		Person person = pf.createPerson(Id.create(1, Person.class));
		Plan plan = pf.createPlan();

		Activity act1 = pf.createActivityFromCoord("exogen", new Coord(1.0, 1.0));
		Leg leg = pf.createLeg("pt");
		Activity act2 = pf.createActivityFromCoord("leisure", new Coord(10.0, 1.0));

		plan.addActivity(act1);
		plan.addLeg(leg);
		plan.addActivity(act2);

		person.addPlan(plan);

		Assert.assertNull(SBBPersonUtils.getHomeActivity(person));
	}

}
