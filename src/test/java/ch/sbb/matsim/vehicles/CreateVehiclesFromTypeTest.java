package ch.sbb.matsim.vehicles;

import java.util.Locale;
import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;

/**
 * @author mrieser
 */
public class CreateVehiclesFromTypeTest {

	private final static String VEHTYPE_GASOLINE = "carGasoline";
	private final static String VEHTYPE_DIESEL = "carDiesel";
	private final static String VEHTYPE_ELECTRIC = "carElectric";
	private final static String VEHTYPE_BIKE = "bike";
	private final static String VEHTYPE_ATTRIBUTE = "vehTypeId";

	private final static String DEFAULT_VEHICLE_TYPE = "defaultCar";

	private Scenario buildTestScenario() {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		Vehicles vehicles = scenario.getVehicles();
		VehiclesFactory vehFac = vehicles.getFactory();

		Id<VehicleType> carGasolineId = Id.create(VEHTYPE_GASOLINE, VehicleType.class);
		Id<VehicleType> carDieselId = Id.create(VEHTYPE_DIESEL, VehicleType.class);
		Id<VehicleType> carElectricId = Id.create(VEHTYPE_ELECTRIC, VehicleType.class);
		Id<VehicleType> bikeId = Id.create(VEHTYPE_BIKE, VehicleType.class);

		VehicleType carGasoline = vehFac.createVehicleType(carGasolineId);
		carGasoline.setMaximumVelocity(180 / 3.6);
		carGasoline.setPcuEquivalents(1.0);

		VehicleType carDiesel = vehFac.createVehicleType(carDieselId);
		carDiesel.setMaximumVelocity(200 / 3.6);
		carDiesel.setPcuEquivalents(1.1);

		VehicleType carElectric = vehFac.createVehicleType(carElectricId);
		carElectric.setMaximumVelocity(140 / 3.6);
		carElectric.setPcuEquivalents(0.8);

		VehicleType bike = vehFac.createVehicleType(bikeId);
		bike.setMaximumVelocity(140 / 3.6);
		bike.setPcuEquivalents(0.8);

		VehicleType carDefault = vehFac.createVehicleType(Id.create(DEFAULT_VEHICLE_TYPE, VehicleType.class));
		carElectric.setMaximumVelocity(145 / 3.6);
		carElectric.setPcuEquivalents(1.0);

		vehicles.addVehicleType(carGasoline);
		vehicles.addVehicleType(carDiesel);
		vehicles.addVehicleType(carElectric);
		vehicles.addVehicleType(carDefault);
		vehicles.addVehicleType(bike);

		Population population = scenario.getPopulation();
		PopulationFactory populationFactory = population.getFactory();

		Person person1 = populationFactory.createPerson(Id.create(1, Person.class));
		Person person2 = populationFactory.createPerson(Id.create(2, Person.class));
		Person person3 = populationFactory.createPerson(Id.create(3, Person.class));
		Person person4 = populationFactory.createPerson(Id.create(4, Person.class));
		Person person5 = populationFactory.createPerson(Id.create(5, Person.class));

		person1.getAttributes().putAttribute(VEHTYPE_ATTRIBUTE, VEHTYPE_GASOLINE);
		person2.getAttributes().putAttribute(VEHTYPE_ATTRIBUTE, VEHTYPE_DIESEL);
		person3.getAttributes().putAttribute(VEHTYPE_ATTRIBUTE, VEHTYPE_ELECTRIC);
		person4.getAttributes().putAttribute(VEHTYPE_ATTRIBUTE, VEHTYPE_GASOLINE);
		person5.getAttributes().putAttribute(VEHTYPE_ATTRIBUTE, VEHTYPE_DIESEL);

		population.addPerson(person1);
		population.addPerson(person2);
		population.addPerson(person3);
		population.addPerson(person4);
		population.addPerson(person5);

		return scenario;
	}

	@Test
	public void testCreateVehicles() {
		Scenario scenario = buildTestScenario();
		Assert.assertTrue(scenario.getVehicles().getVehicles().isEmpty());

		CreateVehiclesFromType vehicleCreator = new CreateVehiclesFromType(scenario.getPopulation(), scenario.getVehicles(), VEHTYPE_ATTRIBUTE, DEFAULT_VEHICLE_TYPE,
				scenario.getConfig().qsim().getMainModes());
		vehicleCreator.createVehicles();

		Assert.assertEquals(10, scenario.getVehicles().getVehicles().size());
		Assert.assertEquals(VEHTYPE_GASOLINE, scenario.getVehicles().getVehicles().get(Id.create("v"+1, Vehicle.class)).getType().getId().toString());
		Assert.assertEquals(VEHTYPE_BIKE, scenario.getVehicles().getVehicles().get(Id.create("vb"+1, Vehicle.class)).getType().getId().toString());
		Assert.assertEquals(VEHTYPE_DIESEL, scenario.getVehicles().getVehicles().get(Id.create("v"+2, Vehicle.class)).getType().getId().toString());
		Assert.assertEquals(VEHTYPE_BIKE, scenario.getVehicles().getVehicles().get(Id.create("vb"+2, Vehicle.class)).getType().getId().toString());
		Assert.assertEquals(VEHTYPE_ELECTRIC, scenario.getVehicles().getVehicles().get(Id.create("v"+3, Vehicle.class)).getType().getId().toString());
		Assert.assertEquals(VEHTYPE_BIKE, scenario.getVehicles().getVehicles().get(Id.create("vb"+3, Vehicle.class)).getType().getId().toString());
		Assert.assertEquals(VEHTYPE_GASOLINE, scenario.getVehicles().getVehicles().get(Id.create("v"+4, Vehicle.class)).getType().getId().toString());
		Assert.assertEquals(VEHTYPE_BIKE, scenario.getVehicles().getVehicles().get(Id.create("vb"+4, Vehicle.class)).getType().getId().toString());
		Assert.assertEquals(VEHTYPE_DIESEL, scenario.getVehicles().getVehicles().get(Id.create("v"+5, Vehicle.class)).getType().getId().toString());
		Assert.assertEquals(VEHTYPE_BIKE, scenario.getVehicles().getVehicles().get(Id.create("vb"+5, Vehicle.class)).getType().getId().toString());
	}

	@Test
	public void testCreateVehicles_missingAttribute() {
		Scenario scenario = buildTestScenario();

		Person person5 = scenario.getPopulation().getPersons().get(Id.create(5, Person.class));
		person5.getAttributes().removeAttribute(VEHTYPE_ATTRIBUTE);

		CreateVehiclesFromType vehicleCreator = new CreateVehiclesFromType(scenario.getPopulation(), scenario.getVehicles(), VEHTYPE_ATTRIBUTE, DEFAULT_VEHICLE_TYPE,
				scenario.getConfig().qsim().getMainModes());
		vehicleCreator.createVehicles();

		Assert.assertEquals(10, scenario.getVehicles().getVehicles().size());
		Assert.assertEquals(VEHTYPE_GASOLINE, scenario.getVehicles().getVehicles().get(Id.create("v"+1, Vehicle.class)).getType().getId().toString());
		Assert.assertEquals(VEHTYPE_DIESEL, scenario.getVehicles().getVehicles().get(Id.create("v"+2, Vehicle.class)).getType().getId().toString());
		Assert.assertEquals(VEHTYPE_ELECTRIC, scenario.getVehicles().getVehicles().get(Id.create("v"+3, Vehicle.class)).getType().getId().toString());
		Assert.assertEquals(VEHTYPE_GASOLINE, scenario.getVehicles().getVehicles().get(Id.create("v"+4, Vehicle.class)).getType().getId().toString());
		Assert.assertEquals(DEFAULT_VEHICLE_TYPE, scenario.getVehicles().getVehicles().get(Id.create("v"+5, Vehicle.class)).getType().getId().toString());
	}

	@Test
	public void testCreateVehicles_missingType() {
		Scenario scenario = buildTestScenario();

		Person person5 = scenario.getPopulation().getPersons().get(Id.create(5, Person.class));
		person5.getAttributes().putAttribute(VEHTYPE_ATTRIBUTE, "carHybrid");

		CreateVehiclesFromType vehicleCreator = new CreateVehiclesFromType(scenario.getPopulation(), scenario.getVehicles(), VEHTYPE_ATTRIBUTE, DEFAULT_VEHICLE_TYPE,
				scenario.getConfig().qsim().getMainModes());
		try {
			vehicleCreator.createVehicles();
			Assert.fail("expected exception, got none.");
		} catch (RuntimeException expected) {
			String message = expected.getMessage();
			// make sure it's the exception we expected
			Assert.assertTrue(message.toLowerCase(Locale.ROOT).contains("vehicletype"));
			Assert.assertTrue(message.toLowerCase(Locale.ROOT).contains("not found"));
			Assert.assertTrue(message.contains("carHybrid"));
		}
	}
}