package ch.sbb.matsim.vehicles;

import ch.sbb.matsim.config.ParkingCostConfigGroup;
import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.events.ParkingCostEvent;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesQueryCache;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityEndEvent;
import org.matsim.api.core.v01.events.PersonArrivalEvent;
import org.matsim.api.core.v01.events.handler.ActivityEndEventHandler;
import org.matsim.api.core.v01.events.handler.PersonArrivalEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.ConfigUtils;

/**
 * @author mrieser
 */
public class RideParkingCostTracker implements PersonArrivalEventHandler, ActivityEndEventHandler {

	private final static Logger log = Logger.getLogger(ParkingCostVehicleTracker.class);
	private final static String TRACKED_MODE = "ride";

	private final Scenario scenario;
	private final ZonesQueryCache zonesQuery;
	private final EventsManager events;
	private final String parkingCostAttributeName;
	private final Map<Id<Person>, Double> arrivalsPerPerson = new HashMap<>();
	private boolean badAttributeTypeWarningShown = false;

	@Inject
	public RideParkingCostTracker(Scenario scenario, ZonesCollection zones, EventsManager events) {
		this.scenario = scenario;
		ParkingCostConfigGroup parkCostConfig = ConfigUtils.addOrGetModule(scenario.getConfig(), ParkingCostConfigGroup.class);
		this.zonesQuery = new ZonesQueryCache(zones.getZones(parkCostConfig.getZonesId()));
		this.parkingCostAttributeName = parkCostConfig.getZonesRideParkingCostAttributeName();
		this.events = events;
	}

	@Override
	public void handleEvent(PersonArrivalEvent event) {
		if (TRACKED_MODE.equals(event.getLegMode())) {
			this.arrivalsPerPerson.put(event.getPersonId(), event.getTime());
		}
	}

	@Override
	public void handleEvent(ActivityEndEvent event) {
		if (SBBActivities.stageActivityTypeList.contains(event.getActType())) {
			// do nothing in the case of a stageActivity
			return;
		}

		Double arrivalTime = this.arrivalsPerPerson.remove(event.getPersonId());
		if (arrivalTime == null || event.getActType().contains("home")) {
			return;
		}

		Link link = this.scenario.getNetwork().getLinks().get(event.getLinkId());
		Zone zone = this.zonesQuery.findZone(link.getCoord().getX(), link.getCoord().getY());
		if (zone == null) {
			return;
		}
		Object value = zone.getAttribute(this.parkingCostAttributeName);
		if (value instanceof Number) {
			double parkDuration = event.getTime() - arrivalTime;
			double hourlyParkingCost = ((Number) value).doubleValue();
			double parkingCost = hourlyParkingCost * (parkDuration / 3600.0);
			this.events.processEvent(new ParkingCostEvent(event.getTime(), event.getPersonId(), null, link.getId(), parkingCost));
		} else if (!this.badAttributeTypeWarningShown) {
			log.error("Ride-ParkingCost attribute must be of type Double or Integer, but is of type " + (value == null ? null : value.getClass()) + ". This message is only given once.");
			this.badAttributeTypeWarningShown = true;
		}
	}

	@Override
	public void reset(int iteration) {
		this.arrivalsPerPerson.clear();
	}
}
