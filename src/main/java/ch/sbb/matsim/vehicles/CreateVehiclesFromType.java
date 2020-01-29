package ch.sbb.matsim.vehicles;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.vehicles.*;

import java.util.Collection;

/**
 * Creates vehicles for each agent, based on the vehicle type in an agent attribute.
 * This class expects the referred vehicle types to already exist in the Vehicles container,
 * and will add a corresponding vehicle for each agent to this Vehicles container.
 * The agent-attribute specifying the vehicle type must of of type String and be stored as
 * an attribute within the agent (see {@link Person#getAttributes()}).
 * If the attribute is missing, or refers to a non-existing vehicle type, a RuntimeException
 * will be thrown.
 *
 * @author mrieser
 */
public class CreateVehiclesFromType {

    private final Population population;
    private final Vehicles vehicles;
    private final String vehicleTypeAttributeName;
    private final String defaultVehicleType;
    private final Collection<String> mainModes;

    public CreateVehiclesFromType(Population population, Vehicles vehicles, String vehicleTypeAttributeName,
                                  String defaultVehicleType, Collection<String> mainModes) {
        this.population = population;
        this.vehicles = vehicles;
        this.vehicleTypeAttributeName = vehicleTypeAttributeName;
        this.defaultVehicleType = defaultVehicleType;
        this.mainModes = mainModes;
    }

    /**
     * @throws RuntimeException when the agent is missing the vehicle type attribute, or the vehicle type is not found in the vehicles container.
     */
    public void createVehicles() {
        VehiclesFactory vf = this.vehicles.getFactory();
        for (Person person : population.getPersons().values()) {
            Id<Person> personId = person.getId();
            Id<Vehicle> vehicleId = Id.create(personId.toString(), Vehicle.class);
            String vehicleTypeName = (String) person.getAttributes().getAttribute(this.vehicleTypeAttributeName);
            if (vehicleTypeName == null) {
                vehicleTypeName = this.defaultVehicleType;
            }
            Id<VehicleType> vehicleTypeId = Id.create(vehicleTypeName, VehicleType.class);
            VehicleType vehicleType = this.vehicles.getVehicleTypes().get(vehicleTypeId);
            if (vehicleType == null) {
                throw new RuntimeException("VehicleType not found: " + vehicleTypeName);
            }
            Vehicle vehicle = vf.createVehicle(vehicleId, vehicleType);
            this.vehicles.addVehicle(vehicle);
            for (String mode : this.mainModes) {
                VehicleUtils.insertVehicleIdIntoAttributes(person, mode, vehicleId);
            }
        }
    }
}
