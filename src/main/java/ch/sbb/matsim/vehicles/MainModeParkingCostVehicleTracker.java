package ch.sbb.matsim.vehicles;

import ch.sbb.matsim.config.ParkingCostConfigGroup;
import ch.sbb.matsim.events.ParkingCostEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleLeavesTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.vehicles.Vehicle;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * @author mrieser
 */
public class MainModeParkingCostVehicleTracker implements ActivityStartEventHandler, VehicleEntersTrafficEventHandler, VehicleLeavesTrafficEventHandler {

	private final static Logger log = LogManager.getLogger(MainModeParkingCostVehicleTracker.class);

	private final Map<Id<Vehicle>, ParkingInfo> parkingPerVehicle = new HashMap<>();
	private final Map<Id<Person>, Id<Vehicle>> lastVehiclePerDriver = new HashMap<>();


	private final String parkingCostAttributeName;
	private final String trackedMode;
	private final Set<String> untrackedActivities;
	private boolean badAttributeTypeWarningShown = false;

	@Inject
	EventsManager events;

	@Inject
	Network network;


	public MainModeParkingCostVehicleTracker(String mode, ParkingCostConfigGroup parkingCostConfigGroup) {
		this.untrackedActivities = parkingCostConfigGroup.getActivityTypesWithoutParkingCost();
		this.parkingCostAttributeName = parkingCostConfigGroup.linkAttributePrefix + mode;
		this.trackedMode = mode;
	}

	@Override
	public void handleEvent(VehicleEntersTrafficEvent event) {
		if (event.getNetworkMode().equals(trackedMode)) {
			ParkingInfo pi = this.parkingPerVehicle.remove(event.getVehicleId());
			if (pi == null) {
				return;
			}
			Link link = network.getLinks().get(pi.parkingLinkId);

			Object value = link.getAttributes().getAttribute(this.parkingCostAttributeName);
			if (value == null) {
				return;
			}
			if (value instanceof Number) {
				double parkDuration = event.getTime() - pi.startParkingTime;
				double hourlyParkingCost = ((Number) value).doubleValue();
				double parkingCost = hourlyParkingCost * (parkDuration / 3600.0);
				this.events.processEvent(new ParkingCostEvent(event.getTime(), pi.driverId, event.getVehicleId(), link.getId(), parkingCost));
			} else if (!this.badAttributeTypeWarningShown) {
				log.error("ParkingCost attribute must be of type Double or Integer, but is of type " + value.getClass() + ". This message is only given once.");
				this.badAttributeTypeWarningShown = true;
			}
		}
	}

	@Override
	public void handleEvent(VehicleLeavesTrafficEvent event) {
		if (event.getNetworkMode().equals(trackedMode)) {
			ParkingInfo pi = new ParkingInfo(event.getLinkId(), event.getPersonId(), event.getTime());
			this.parkingPerVehicle.put(event.getVehicleId(), pi);
			this.lastVehiclePerDriver.put(event.getPersonId(), event.getVehicleId());
		}
	}

	@Override
	public void handleEvent(ActivityStartEvent event) {
		if (this.untrackedActivities.stream().anyMatch(s -> event.getActType().contains(s))) {
			Id<Vehicle> vehicleId = this.lastVehiclePerDriver.get(event.getPersonId());
			if (vehicleId != null) {
				this.parkingPerVehicle.remove(vehicleId);
			}
		}
	}

	@Override
	public void reset(int iteration) {
		this.parkingPerVehicle.clear();
		this.lastVehiclePerDriver.clear();
	}

	private static class ParkingInfo {

		final Id<Link> parkingLinkId;
		final Id<Person> driverId;
		final double startParkingTime;

		ParkingInfo(Id<Link> parkingLinkId, Id<Person> driverId, double startParkingTime) {
			this.parkingLinkId = parkingLinkId;
			this.driverId = driverId;
			this.startParkingTime = startParkingTime;
		}
	}
}
