package ch.sbb.matsim.events;

import ch.sbb.matsim.config.ParkingCostConfigGroup;
import ch.sbb.matsim.vehicles.MainModeParkingCostVehicleTracker;
import ch.sbb.matsim.vehicles.TeleportedModeParkingCostTracker;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;

import java.util.Collection;

public class ParkingCostModule extends AbstractModule {

    @Override
    public void install() {
        ParkingCostConfigGroup parkingCostConfigGroup = ConfigUtils.addOrGetModule(getConfig(), ParkingCostConfigGroup.class);
        Collection<String> mainModes = switch (getConfig().controler().getMobsim()) {
            case "qsim" -> getConfig().qsim().getMainModes();
            case "hermes" -> getConfig().hermes().getMainModes();
            default -> throw new RuntimeException("ParkingCosts are currently supported for Qsim and Hermes");
        };
        for (String mode : parkingCostConfigGroup.getModesWithParkingCosts()) {
            if (mainModes.contains(mode)) {
                addEventHandlerBinding().toInstance(new MainModeParkingCostVehicleTracker(mode, parkingCostConfigGroup));
            } else {
                addEventHandlerBinding().toInstance(new TeleportedModeParkingCostTracker(mode, parkingCostConfigGroup));
            }
        }
    }
}
