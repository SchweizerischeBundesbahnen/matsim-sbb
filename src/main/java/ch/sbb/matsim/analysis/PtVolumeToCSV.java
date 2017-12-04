/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.PersonEntersVehicleEvent;
import org.matsim.api.core.v01.events.PersonLeavesVehicleEvent;
import org.matsim.api.core.v01.events.TransitDriverStartsEvent;
import org.matsim.api.core.v01.events.handler.PersonEntersVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.PersonLeavesVehicleEventHandler;
import org.matsim.api.core.v01.events.handler.TransitDriverStartsEventHandler;
import org.matsim.core.api.experimental.events.VehicleArrivesAtFacilityEvent;
import org.matsim.core.api.experimental.events.VehicleDepartsAtFacilityEvent;
import org.matsim.core.api.experimental.events.handler.VehicleArrivesAtFacilityEventHandler;
import org.matsim.core.api.experimental.events.handler.VehicleDepartsAtFacilityEventHandler;
import org.matsim.vehicles.Vehicle;
import ch.sbb.matsim.calibration.PTObjective;
import ch.sbb.matsim.csv.CSVWriter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;


public class PtVolumeToCSV implements TransitDriverStartsEventHandler,
        VehicleArrivesAtFacilityEventHandler,
        PersonEntersVehicleEventHandler,
        PersonLeavesVehicleEventHandler, VehicleDepartsAtFacilityEventHandler {

    public static final String FILENAME_STOPS =  "matsim_stops.csv";
    public static final String FILENMAE_VEHJOURNEYS = "matsim_vehjourneys.csv";

    public static final String COL_STOP_ID = "stop_id";
    public static final String COL_FROM_STOP_ID = "from_stop_id";
    public static final String COL_TO_STOP_ID = "to_stop_id";
    public static final String COL_INDEX = "index";
    public static final String COL_BOARDING = "boarding";
    public static final String COL_ALIGHTING = "alighting";
    public static final String COL_LINE = "line";
    public static final String COL_LINEROUTE = "lineroute";
    public static final String COL_DEPARTURE_ID = "departure_id";
    public static final String COL_VEHICLE_ID = "vehicle_id";
    public static final String COL_ARRIVAL = "arrival";
    public static final String COL_DEPARTURE = "departure";
    public static final String COL_PASSENGERS = "passengers";

    public static final String[] COLS_STOPS = new String[]{COL_INDEX, COL_STOP_ID, COL_BOARDING, COL_ALIGHTING, COL_LINE, COL_LINEROUTE, COL_DEPARTURE_ID, COL_VEHICLE_ID, COL_DEPARTURE, COL_ARRIVAL};
    public static final String[] COLS_VEHJOURNEYS = new String[]{COL_INDEX, COL_FROM_STOP_ID, COL_TO_STOP_ID, COL_PASSENGERS, COL_LINE, COL_LINEROUTE, COL_DEPARTURE_ID, COL_VEHICLE_ID, COL_DEPARTURE, COL_ARRIVAL};

    Logger log = Logger.getLogger(PTObjective.class);

    private Map<Id, PTVehicle> ptVehicles = new HashMap<>();
    private HashSet<Id> ptAgents = new HashSet<>();
    private CSVWriter stopsWriter = new CSVWriter(COLS_STOPS);
    private CSVWriter vehJourneyWriter = new CSVWriter(COLS_VEHJOURNEYS);

    // Methods
    @Override
    public void reset(int iteration) {
        stopsWriter.clear();
        vehJourneyWriter.clear();
        ptAgents.clear();
        ptVehicles.clear();
    }

    public void write(String path){
        stopsWriter.write(path + "/" + FILENAME_STOPS);
        vehJourneyWriter.write(path+ "/" + FILENMAE_VEHJOURNEYS);
    }

    @Override
    public void handleEvent(TransitDriverStartsEvent event) {
        PTVehicle ptVehilce = new PTVehicle(event.getTransitLineId(), event.getTransitRouteId(), event.getDepartureId(), event.getVehicleId());
        ptVehicles.put(event.getVehicleId(), ptVehilce);
        ptAgents.add(event.getDriverId());
    }

    @Override
    public void handleEvent(VehicleDepartsAtFacilityEvent event) {
        Id<Vehicle> vId = event.getVehicleId();
        if(ptVehicles.containsKey(vId)){
            PTVehicle ptVehicle = ptVehicles.get(vId);
            ptVehicle.setDeparture(event.getTime(), event.getFacilityId());
        }
    }

    @Override
    public void handleEvent(VehicleArrivesAtFacilityEvent event) {
        Id<Vehicle> vId = event.getVehicleId();
        if(ptVehicles.containsKey(vId)){
            PTVehicle ptVehicle = ptVehicles.get(vId);
            ptVehicle.setArrival(event.getTime(), event.getFacilityId());
        }
    }

    @Override
    public void handleEvent(PersonEntersVehicleEvent event) {
        Id<Vehicle> vId = event.getVehicleId();

        if(ptAgents.contains(event.getPersonId())){return;}

        if(ptVehicles.containsKey(vId)){
            PTVehicle ptVehicle = ptVehicles.get(vId);
            ptVehicle.addPassenger();
        }
    }

    @Override
    public void handleEvent(PersonLeavesVehicleEvent event) {
        if(ptAgents.contains(event.getPersonId())){return;}
        Id<Vehicle> vId = event.getVehicleId();
        if(ptVehicles.containsKey(vId)){
            PTVehicle ptVehicle = ptVehicles.get(vId);
            ptVehicle.removePassenger();
        }
    }


    // Private classes
    private class PTVehicle {

        // Attributes
        private final Id transitLineId;
        private final Id transitRouteId;
        private final Id departureId;
        private final Id vehicleId;
        private double passengers = 0;
        private double boardings = 0;
        private double alightings = 0;

        private double departure = 0;
        private double arrival = 0;
        private Id last_stop = null;
        private int indexVehJourney = 0;
        private int indexStops = 0;

        // Constructors
        public PTVehicle(Id transitLineId, Id transitRouteId, Id departureId, Id<Vehicle> vehicleId) {
            this.transitLineId = transitLineId;
            this.transitRouteId = transitRouteId;
            this.departureId = departureId;
            this.vehicleId = vehicleId;
        }

        public void addPassenger() {
            this.boardings += 1;
            this.passengers += 1;
        }

        public void removePassenger() {
            this.alightings += 1;
            this.passengers -= 1;
        }

        public void setDeparture(double departure, Id stop) {
            this.departure = departure;

            HashMap<String, String> row = stopsWriter.addRow();
            row.put(COL_INDEX, Integer.toString(this.indexStops));
            row.put(COL_STOP_ID, stop.toString());
            row.put(COL_BOARDING, Double.toString(this.boardings));
            row.put(COL_ALIGHTING, Double.toString(this.alightings));
            row.put(COL_LINE, this.transitLineId.toString());
            row.put(COL_LINEROUTE, this.transitRouteId.toString());
            row.put(COL_DEPARTURE_ID, this.departureId.toString());
            row.put(COL_VEHICLE_ID, this.vehicleId.toString());
            row.put(COL_DEPARTURE, Double.toString(this.departure));
            row.put(COL_ARRIVAL, Double.toString(this.arrival));

            this.alightings = 0.0;
            this.boardings = 0.0;
            this.indexStops += 1;
        }

        public void setArrival(double arrival, Id stop) {

            if(this.last_stop != null) {
                HashMap<String, String> row = vehJourneyWriter.addRow();
                row.put(COL_INDEX, Integer.toString(this.indexVehJourney));
                row.put(COL_FROM_STOP_ID, this.last_stop.toString());
                row.put(COL_TO_STOP_ID, stop.toString());
                row.put(COL_PASSENGERS, Double.toString(this.passengers));
                row.put(COL_LINE, this.transitLineId.toString());
                row.put(COL_LINEROUTE, this.transitRouteId.toString());
                row.put(COL_DEPARTURE_ID, this.departureId.toString());
                row.put(COL_VEHICLE_ID, this.vehicleId.toString());
                row.put(COL_DEPARTURE, Double.toString(this.departure));
                row.put(COL_ARRIVAL, Double.toString(arrival));
            }
            this.last_stop = stop;
            this.arrival = arrival;
            this.indexVehJourney += 1;
        }
    }
}
