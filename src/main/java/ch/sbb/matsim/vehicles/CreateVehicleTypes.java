package ch.sbb.matsim.vehicles;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleTypeImpl;
import org.matsim.vehicles.VehicleWriterV1;
import org.matsim.vehicles.Vehicles;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class CreateVehicleTypes {

    public static void main(String[] args) throws IOException {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        Map<String, Double> typeToPCE = new HashMap<>();
        typeToPCE.put("car", 1.0);
        typeToPCE.put("van", 1.2);
        typeToPCE.put("hgv", 2.0);
        typeToPCE.put("hgva", 2.5);

        createVehicleTypes(scenario.getVehicles(), typeToPCE);
        writeVehicleTypes(scenario.getVehicles(), args[0]);
    }

    private static void createVehicleTypes(Vehicles vehicles, Map<String, Double> typeToPCE) {
        VehiclesFactory vf = vehicles.getFactory();
        for(Map.Entry<String, Double> typeStr: typeToPCE.entrySet())    {
            Id<VehicleType> typeId = Id.create(typeStr.getKey(), VehicleType.class);
            VehicleType type = vf.createVehicleType(typeId);
            type.setPcuEquivalents(typeStr.getValue());

            vehicles.addVehicleType(type);
        }
    }

    private static void writeVehicleTypes(Vehicles vehicles, String output) throws IOException {
        new MatsimVehicleWriter(vehicles).writeFile(output);
    }
}
