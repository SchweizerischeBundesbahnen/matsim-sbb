package ch.sbb.matsim.utils;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet;
import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.VehicleUtils;

import static org.junit.Assert.assertFalse;

public class ScenarioConsistencyCheckerTest {

	public static final String HOME = SBBActivities.home;
	public static final String WORK = SBBActivities.work+"_23";
	private Person orderlyPerson;
	private Person personWithSpaceship;
	private Person personWithLackingAttribute;
	private Person personWithMismatchingLegs;
	private Person personWithUnknownActivity;
	private Person carlessPersonUsingCar;

	@Test
	public void checkCorrectPopulation() {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		createPersons(scenario);
		scenario.getPopulation().addPerson(orderlyPerson);
		ScenarioConsistencyChecker.checkScenarioConsistency(scenario);
	}

	@Test
	public void checkVehicleNamespace() {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		scenario.getVehicles().addVehicleType(VehicleUtils.getDefaultVehicleType());
		scenario.getTransitVehicles().addVehicleType(VehicleUtils.getDefaultVehicleType());
		scenario.getVehicles().addVehicle(VehicleUtils.createVehicle(Id.createVehicleId(1),VehicleUtils.getDefaultVehicleType()));
		scenario.getTransitVehicles().addVehicle(VehicleUtils.createVehicle(Id.createVehicleId(1),VehicleUtils.getDefaultVehicleType()));
		assertFalse(ScenarioConsistencyChecker.checkVehicles(scenario));
	}


	@Test
	public void checkWrongPlanElements() {
		Assertions.assertThrows(RuntimeException.class, () -> {
			Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
			createPersons(scenario);
			scenario.getPopulation().addPerson(personWithMismatchingLegs);
			ScenarioConsistencyChecker.checkScenarioConsistency(scenario);
		});
	}

	@Test
	public void checkWrongModes() {
		Assertions.assertThrows(RuntimeException.class, () -> {
			Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
			createPersons(scenario);
			scenario.getPopulation().addPerson(personWithSpaceship);
			ScenarioConsistencyChecker.checkScenarioConsistency(scenario);
		});
	}

	@Test
	public void checkWrongActivities() {
		Assertions.assertThrows(RuntimeException.class, () -> {
			Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
			createPersons(scenario);
			scenario.getPopulation().addPerson(personWithUnknownActivity);
			ScenarioConsistencyChecker.checkScenarioConsistency(scenario);
		});
	}

	@Test
	public void checkMissingAttributes() {
		Assertions.assertThrows(RuntimeException.class, () -> {
			Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
			createPersons(scenario);
			scenario.getPopulation().addPerson(personWithLackingAttribute);
			ScenarioConsistencyChecker.checkScenarioConsistency(scenario);
		});
    }

	@Test
    public void checkHasNoCar() {
		Assertions.assertThrows(RuntimeException.class, () -> {
			Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
			createPersons(scenario);
			scenario.getPopulation().addPerson(carlessPersonUsingCar);
			ScenarioConsistencyChecker.checkScenarioConsistency(scenario);
		});
    }

    @Test
    public void checkIntermodalAttributes() {
        final Config config = ConfigUtils.createConfig();
        SwissRailRaptorConfigGroup swissRailRaptorConfigGroup = new SwissRailRaptorConfigGroup();
        IntermodalAccessEgressParameterSet ae = new IntermodalAccessEgressParameterSet();
        ae.setMode("bike_feeder");
        ae.setStopFilterAttribute("bikeAccessible");
        swissRailRaptorConfigGroup.addIntermodalAccessEgress(ae);
        swissRailRaptorConfigGroup.setUseIntermodalAccessEgress(true);
        config.addModule(swissRailRaptorConfigGroup);
        Scenario scenario = ScenarioUtils.createScenario(config);
        assertFalse(ScenarioConsistencyChecker.checkIntermodalAttributesAtStops(scenario));
    }

    private void createPersons(Scenario scenario) {
        {
            orderlyPerson = scenario.getPopulation().getFactory().createPerson(Id.createPersonId(1));
            Variables.DEFAULT_PERSON_ATTRIBUTES.forEach(s -> orderlyPerson.getAttributes().putAttribute(s, "1"));
            PopulationUtils.putSubpopulation(orderlyPerson, Variables.REGULAR);
            Plan plan = scenario.getPopulation().getFactory().createPlan();
            plan.addActivity(scenario.getPopulation().getFactory().createActivityFromCoord(HOME, new Coord(0, 0)));
            plan.addLeg(scenario.getPopulation().getFactory().createLeg(SBBModes.CAR));
            plan.addActivity(scenario.getPopulation().getFactory().createActivityFromCoord(WORK, new Coord(0, 0)));
			orderlyPerson.addPlan(plan);
		}
		{
			personWithSpaceship = scenario.getPopulation().getFactory().createPerson(Id.createPersonId(1));
			Variables.DEFAULT_PERSON_ATTRIBUTES.forEach(s -> personWithSpaceship.getAttributes().putAttribute(s, "1"));
			PopulationUtils.putSubpopulation(personWithSpaceship, Variables.REGULAR);
			Plan plan = scenario.getPopulation().getFactory().createPlan();
			plan.addActivity(scenario.getPopulation().getFactory().createActivityFromCoord(HOME, new Coord(0, 0)));
			plan.addLeg(scenario.getPopulation().getFactory().createLeg("spaceship"));
			plan.addActivity(scenario.getPopulation().getFactory().createActivityFromCoord(WORK, new Coord(0, 0)));
			personWithSpaceship.addPlan(plan);
		}

		{
			personWithUnknownActivity = scenario.getPopulation().getFactory().createPerson(Id.createPersonId(1));
			Variables.DEFAULT_PERSON_ATTRIBUTES.forEach(s -> personWithUnknownActivity.getAttributes().putAttribute(s, "1"));
			PopulationUtils.putSubpopulation(personWithUnknownActivity, Variables.REGULAR);
			Plan plan = scenario.getPopulation().getFactory().createPlan();
			plan.addActivity(scenario.getPopulation().getFactory().createActivityFromCoord("SINGINGLOUDLY", new Coord(0, 0)));
			plan.addLeg(scenario.getPopulation().getFactory().createLeg(SBBModes.CAR));
			plan.addActivity(scenario.getPopulation().getFactory().createActivityFromCoord(WORK, new Coord(0, 0)));
			personWithUnknownActivity.addPlan(plan);
		}

		{
			personWithLackingAttribute = scenario.getPopulation().getFactory().createPerson(Id.createPersonId(1));
			Variables.DEFAULT_PERSON_ATTRIBUTES.forEach(s -> personWithLackingAttribute.getAttributes().putAttribute(s, "1"));
			personWithLackingAttribute.getAttributes().removeAttribute(Variables.PT_SUBSCRIPTION);
			PopulationUtils.putSubpopulation(personWithLackingAttribute, Variables.REGULAR);
			Plan plan2 = scenario.getPopulation().getFactory().createPlan();
			plan2.addActivity(scenario.getPopulation().getFactory().createActivityFromCoord(HOME, new Coord(0, 0)));
			plan2.addLeg(scenario.getPopulation().getFactory().createLeg(SBBModes.CAR));
			plan2.addActivity(scenario.getPopulation().getFactory().createActivityFromCoord(WORK, new Coord(0, 0)));
			personWithLackingAttribute.addPlan(plan2);
		}

		{
			personWithMismatchingLegs = scenario.getPopulation().getFactory().createPerson(Id.createPersonId(1));
			Variables.DEFAULT_PERSON_ATTRIBUTES.forEach(s -> personWithMismatchingLegs.getAttributes().putAttribute(s, "1"));
			PopulationUtils.putSubpopulation(personWithMismatchingLegs, Variables.REGULAR);
			Plan wrongPlan = scenario.getPopulation().getFactory().createPlan();
			wrongPlan.addActivity(scenario.getPopulation().getFactory().createActivityFromCoord(HOME, new Coord(0, 0)));
			wrongPlan.addLeg(scenario.getPopulation().getFactory().createLeg(SBBModes.CAR));
			wrongPlan.addLeg(scenario.getPopulation().getFactory().createLeg(SBBModes.CAR));
			wrongPlan.addActivity(scenario.getPopulation().getFactory().createActivityFromCoord(WORK, new Coord(0, 0)));
			personWithMismatchingLegs.addPlan(wrongPlan);
		}

		{
		carlessPersonUsingCar = scenario.getPopulation().getFactory().createPerson(Id.createPersonId(1));
		Variables.DEFAULT_PERSON_ATTRIBUTES.forEach(s -> carlessPersonUsingCar.getAttributes().putAttribute(s,"0"));
		PopulationUtils.putSubpopulation(carlessPersonUsingCar,Variables.REGULAR);
		Plan carplan = scenario.getPopulation().getFactory().createPlan();
		carplan.addActivity(scenario.getPopulation().getFactory().createActivityFromCoord(HOME,new Coord(0,0)));
		carplan.addLeg(scenario.getPopulation().getFactory().createLeg(SBBModes.CAR));
		carplan.addActivity(scenario.getPopulation().getFactory().createActivityFromCoord(WORK,new Coord(0,0)));
		carlessPersonUsingCar.addPlan(carplan);
		}

	}
}