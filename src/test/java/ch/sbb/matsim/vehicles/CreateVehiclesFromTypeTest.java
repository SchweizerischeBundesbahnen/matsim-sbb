package ch.sbb.matsim.vehicles;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.Vehicles;
import org.matsim.vehicles.VehiclesFactory;

/**
 * @author mrieser
 */
public class CreateVehiclesFromTypeTest {


	private Scenario buildTestScenario() {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		Vehicles vehicles = scenario.getVehicles();
		VehiclesFactory vehFac = vehicles.getFactory();
        Id<VehicleType> bikeId = Id.create(SBBModes.BIKE, VehicleType.class);
        Id<VehicleType> ebikeId = Id.create(SBBModes.EBIKE, VehicleType.class);
		VehicleType bike = vehFac.createVehicleType(bikeId);
		bike.setMaximumVelocity(140 / 3.6);
		bike.setPcuEquivalents(0.8);

        VehicleType ebike = vehFac.createVehicleType(ebikeId);
        bike.setMaximumVelocity(140 / 3.6);
        bike.setPcuEquivalents(0.8);

        VehicleType car = vehFac.createVehicleType(Id.create(SBBModes.CAR, VehicleType.class));
        car.setMaximumVelocity(145 / 3.6);
        car.setPcuEquivalents(1.0);

        VehicleType truck = vehFac.createVehicleType(Id.create("hgva", VehicleType.class));
        car.setMaximumVelocity(90 / 3.6);
        car.setPcuEquivalents(5.0);


        vehicles.addVehicleType(car);
		vehicles.addVehicleType(bike);
        vehicles.addVehicleType(ebike);
        vehicles.addVehicleType(truck);

		Population population = scenario.getPopulation();
		PopulationFactory populationFactory = population.getFactory();

		Person person1 = populationFactory.createPerson(Id.create(1, Person.class));
        Person trucker = populationFactory.createPerson(Id.create("5_LZ_1", Person.class));
        PopulationUtils.putSubpopulation(trucker, Variables.FREIGHT_ROAD);


		population.addPerson(person1);
        population.addPerson(trucker);

		return scenario;
	}

	@Test
	public void testCreateVehicles() {
		Scenario scenario = buildTestScenario();
		Assert.assertTrue(scenario.getVehicles().getVehicles().isEmpty());

        CreateAgentVehicles vehicleCreator = new CreateAgentVehicles(scenario.getPopulation(), scenario.getVehicles(),
				scenario.getConfig().qsim().getMainModes());
		vehicleCreator.createVehicles();

        Assert.assertEquals(4, scenario.getVehicles().getVehicles().size());
        Assert.assertEquals(SBBModes.CAR, scenario.getVehicles().getVehicles().get(Id.create("v" + 1, Vehicle.class)).getType().getId().toString());
        Assert.assertEquals(SBBModes.BIKE, scenario.getVehicles().getVehicles().get(Id.create("v_bike_" + 1, Vehicle.class)).getType().getId().toString());

        Assert.assertEquals("hgva", scenario.getVehicles().getVehicles().get(Id.create("v5_LZ_1", Vehicle.class)).getType().getId().toString());
        Assert.assertEquals(SBBModes.BIKE, scenario.getVehicles().getVehicles().get(Id.create("v_bike_5_LZ_1", Vehicle.class)).getType().getId().toString());

	}

}