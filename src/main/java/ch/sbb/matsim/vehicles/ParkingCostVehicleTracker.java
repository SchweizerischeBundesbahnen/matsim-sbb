package ch.sbb.matsim.vehicles;

import ch.sbb.matsim.analysis.LocateAct;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.ActivityStartEvent;
import org.matsim.api.core.v01.events.PersonMoneyEvent;
import org.matsim.api.core.v01.events.VehicleEntersTrafficEvent;
import org.matsim.api.core.v01.events.VehicleLeavesTrafficEvent;
import org.matsim.api.core.v01.events.handler.ActivityStartEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleEntersTrafficEventHandler;
import org.matsim.api.core.v01.events.handler.VehicleLeavesTrafficEventHandler;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.vehicles.Vehicle;
import org.opengis.feature.simple.SimpleFeature;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

public class ParkingCostVehicleTracker implements ActivityStartEventHandler, VehicleEntersTrafficEventHandler, VehicleLeavesTrafficEventHandler {

    private final static Logger log = Logger.getLogger(ParkingCostVehicleTracker.class);

    private final Map<Id<Vehicle>, ParkingInfo> parkingPerVehicle = new HashMap<>();
    private final Map<Id<Person>, Id<Vehicle>> lastVehiclePerDriver = new HashMap<>();
    private final Scenario scenario;
    private final LocateAct locateAct;
    private final EventsManager events;
    private final String parkingCostAttributeName;
    private boolean badAttributeTypeWarningShown = false;

    @Inject
    public ParkingCostVehicleTracker(Scenario scenario, LocateAct locateAct, EventsManager events) {
        this.scenario = scenario;
        this.locateAct = locateAct;
        this.parkingCostAttributeName = "parkingCost"; // TODO currently hard-coded, should come from config later.
        this.events = events;
    }

    @Override
    public void handleEvent(VehicleEntersTrafficEvent event) {
        ParkingInfo pi = this.parkingPerVehicle.remove(event.getVehicleId());
        if (pi == null) {
            return;
        }
        Link link = this.scenario.getNetwork().getLinks().get(pi.parkingLinkId);
        SimpleFeature zone = this.locateAct.getZone(link.getCoord());
        if (zone == null) {
            return;
        }
        Object value = zone.getAttribute(this.parkingCostAttributeName);
        if (value instanceof Double) {
            double parkingCost = (Double) value;
            this.events.processEvent(new PersonMoneyEvent(event.getTime(), pi.driverId, parkingCost));
        } else if (!this.badAttributeTypeWarningShown) {
            log.error("ParkingCost attribute must be of type Double, but is of type " + (value == null ? null : value.getClass()) + ". This message is only given once.");
            this.badAttributeTypeWarningShown = true;
        }

    }

    @Override
    public void handleEvent(VehicleLeavesTrafficEvent event) {
        ParkingInfo pi = new ParkingInfo(event.getLinkId(), event.getPersonId(), event.getTime());
        this.parkingPerVehicle.put(event.getVehicleId(), pi);
        this.lastVehiclePerDriver.put(event.getPersonId(), event.getVehicleId());
    }

    @Override
    public void handleEvent(ActivityStartEvent event) {
        if ("home".equals(event.getActType())) {
            // don't track the vehicle parking if the agent is at home, assuming the agent does not have to pay at his home location
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
