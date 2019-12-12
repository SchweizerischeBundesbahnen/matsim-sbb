package ch.sbb.matsim.mavi.pt;

import ch.sbb.matsim.mavi.visum.Visum;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.vehicles.*;
import org.matsim.vehicles.VehicleType.DoorOperationMode;

import java.util.stream.IntStream;

public class VehicleTypeExporter {

    private static final Logger log = Logger.getLogger(VehicleTypeExporter.class);

    private final Vehicles vehicles;
    private VehiclesFactory vehicleBuilder;

    public VehicleTypeExporter(Scenario scenario)   {
        this.vehicles = scenario.getVehicles();
        this.vehicleBuilder = scenario.getVehicles().getFactory();
    }

    public void createVehicleTypes(Visum visum) {
        Visum.ComObject tSystems = visum.getNetObject("TSystems");
        int nrOfTSystems = tSystems.countActive();
        log.info("loading " + nrOfTSystems + " vehicle types...");

        String[][] tSystemAttributes = Visum.getArrayFromAttributeList(nrOfTSystems, tSystems,
                "Code", "Name");
        IntStream.range(0, nrOfTSystems).forEach(i -> {
            String tSysCode = tSystemAttributes[i][0];
            String tSysName = tSystemAttributes[i][1];

            Id<VehicleType> vehicleTypeId = Id.create(tSysCode, VehicleType.class);
            // TODO e.g. for "Fernbusse", we need the possibility to set capacity constraints.
            VehicleType vehicleType = this.vehicleBuilder.createVehicleType(vehicleTypeId);
            vehicleType.setDescription(tSysName);
            VehicleUtils.setDoorOperationMode(vehicleType, DoorOperationMode.serial);
            VehicleCapacity vehicleCapacity = vehicleType.getCapacity();
            vehicleCapacity.setStandingRoom(500);
            vehicleCapacity.setSeats(10000);

            // the following parameters do not have any influence in a deterministic simulation engine
            vehicleType.setLength(10);
            vehicleType.setWidth(2);
            vehicleType.setPcuEquivalents(1);
            vehicleType.setMaximumVelocity(10000);
            this.vehicles.addVehicleType(vehicleType);
        });

        log.info("finished loading vehicle types...");
    }
}