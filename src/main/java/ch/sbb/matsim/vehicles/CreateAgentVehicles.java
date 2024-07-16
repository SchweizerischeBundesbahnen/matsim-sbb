package ch.sbb.matsim.vehicles;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.population.PopulationUtils;
import org.matsim.vehicles.*;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Creates vehicles for each agent, based on the vehicle type in an agent attribute. This class expects the referred vehicle types to already exist in the Vehicles container, and will add a
 * corresponding vehicle for each agent to this Vehicles container. The agent-attribute specifying the vehicle type must of of type String and be stored as an attribute within the agent (see {@link
 * Person#getAttributes()}). If the attribute is missing, or refers to a non-existing vehicle type, a RuntimeException will be thrown.
 *
 * @author mrieser
 */
public class CreateAgentVehicles {

	private final Population population;
	private final Vehicles vehicles;
	private final String defaultVehicleType;
	private final Collection<String> mainModes;
	private final Logger log = LogManager.getLogger(CreateAgentVehicles.class);

	public CreateAgentVehicles(Population population, Vehicles vehicles, Collection<String> mainModes) {
		this.population = population;
		this.vehicles = vehicles;
		this.defaultVehicleType = SBBModes.CAR;
		this.mainModes = mainModes;
	}

	/**
	 * @throws RuntimeException when the agent is missing the vehicle type attribute, or the vehicle type is not found in the vehicles container.
	 */
	public void createVehicles() {
		VehiclesFactory vf = this.vehicles.getFactory();
		VehicleType vehicleTypeBike = this.vehicles.getVehicleTypes().get(Id.create(SBBModes.BIKE, VehicleType.class));
		VehicleType vehicleTypeEBike = this.vehicles.getVehicleTypes().get(Id.create(SBBModes.EBIKE, VehicleType.class));
		for (Person person : population.getPersons().values()) {
			String vehicleTypeName = defaultVehicleType;
			if (Variables.FREIGHT_ROAD.equals(PopulationUtils.getSubpopulation(person))) {
				String lowerCasePersonId = person.getId().toString().toLowerCase();
				if (lowerCasePersonId.contains("_li_")) {
					vehicleTypeName = "van";
				} else if (lowerCasePersonId.contains("_lz_")) {
					vehicleTypeName = "hgva";
				} else if (lowerCasePersonId.contains("_lw_")) {
					vehicleTypeName = "hgv";
				} else {
					//should not happen in any typical mobi setup
					log.error("Could not determine vehicle type for freight agent id=" + person.getId() + ". Assuming van...");
					vehicleTypeName = "van";
				}
			}
			Id<Person> personId = person.getId();
			Id<Vehicle> vehicleId = Id.create("v"+personId.toString(), Vehicle.class);
			Id<VehicleType> vehicleTypeId = Id.create(vehicleTypeName, VehicleType.class);
			VehicleType vehicleType = this.vehicles.getVehicleTypes().get(vehicleTypeId);
			Vehicle vehicle = vf.createVehicle(vehicleId, vehicleType);
			this.vehicles.addVehicle(vehicle);

			Id<Vehicle> vehicleIdBike = Id.create("v_bike_" + personId, Vehicle.class);
			boolean hasEBike = String.valueOf(person.getAttributes().getAttribute(Variables.EBIKE_AVAIL)).equals(Variables.AVAIL_TRUE);

			Vehicle vehicleBike = vf.createVehicle(vehicleIdBike, hasEBike ? vehicleTypeEBike : vehicleTypeBike);
			this.vehicles.addVehicle(vehicleBike);

			var vehicleMap = this.mainModes.stream().collect(Collectors.toMap(s -> s, t -> vehicleId));
			vehicleMap.put(SBBModes.BIKE, vehicleIdBike);
			VehicleUtils.insertVehicleIdsIntoAttributes(person, vehicleMap);

		}
	}
}
