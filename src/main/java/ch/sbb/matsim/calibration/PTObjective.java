/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.calibration;

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
import org.matsim.core.utils.misc.Counter;
import org.matsim.vehicles.Vehicle;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.csv.CSVReader;

import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class PTObjective implements TransitDriverStartsEventHandler,
        VehicleArrivesAtFacilityEventHandler,
        PersonEntersVehicleEventHandler,
        PersonLeavesVehicleEventHandler, VehicleDepartsAtFacilityEventHandler {
    Scenario scenario;

    CSVWriter csvWriter = new CSVWriter(new String[]{"line", "lineRoute", "departureId", "facilityId", "passengers", "visum_passengers"});

    Logger log = Logger.getLogger(PTObjective.class);
    private Map<Id, PTVehicle> ptVehicles = new HashMap<>();
    private HashSet<Id> ptAgents = new HashSet<>();
    private CSVReader visumVolume = new CSVReader(new String[]{"line", "lineRoute", "departureId", "facilityId", "passengers"});
    private HashMap<Set<String>, Float> visumData = new HashMap<Set<String>, Float>();


    public PTObjective(Scenario scenario, final String csvVolume){

        this.visumVolume.read(csvVolume, ",");
        this.scenario = scenario;

        for(HashMap<String, String> d :this.visumVolume.data){
            Set<String> key = new HashSet<>();
            key.add(d.get("line"));
            key.add(d.get("lineRoute"));
            key.add(d.get("departure"));
            key.add(d.get("facilityId"));
            this.visumData.put(key, Float.parseFloat(d.get("passengers")));
        }
    }

    @Override
    public void handleEvent(TransitDriverStartsEvent event) {
        PTVehicle ptVehilce = new PTVehicle(event.getTransitLineId(), event.getTransitRouteId(), event.getDepartureId());
        ptVehicles.put(event.getVehicleId(), ptVehilce);
        ptAgents.add(event.getDriverId());
    }

    @Override
    public void handleEvent(VehicleDepartsAtFacilityEvent event) {
        Id<Vehicle> vId = event.getVehicleId();
        if(ptVehicles.containsKey(vId)){
            PTVehicle ptVehicle = ptVehicles.get(vId);
            ptVehicle.setLastStop(event.getFacilityId());

            HashMap<String, String> row = this.csvWriter.addRow();
            row.put("line", ptVehicle.getTransitLineId().toString());
            row.put("lineRoute", ptVehicle.getTransitLineRouteId().toString());
            row.put("departureId", ptVehicle.getDepartureId().toString());
            row.put("facilityId", event.getFacilityId().toString());
            row.put("passengers", String.valueOf(ptVehicle.getPassengers()));
            Set<String> key = new HashSet<>();
            key.add(row.get("line"));
            key.add(row.get("lineRoute"));
            key.add(row.get("departure"));
            key.add(row.get("facilityId"));
            row.put("visum_passengers", String.valueOf(this.visumData.get(key)));
        }
    }

    @Override
    public void handleEvent(VehicleArrivesAtFacilityEvent event) {
        Id<Vehicle> vId = event.getVehicleId();
        if(ptVehicles.containsKey(vId)){
            PTVehicle ptVehicle = ptVehicles.get(vId);
            ptVehicle.setLastStop(event.getFacilityId());
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

    // Methods
    @Override
    public void reset(int iteration) {
    }

    public double getScore(){
        double score = 0.0;
        for (HashMap<String, String> d : this.csvWriter.getData()) {
            score += Math.pow(Double.parseDouble(d.get("passengers")) - Double.parseDouble(d.get("visum_passengers")), 2);
        }

        return score;
    }

    public void write(String path) {
        this.csvWriter.write(path);
    }

    // Private classes
    private class PTVehicle {

        // Attributes
        private final Id transitLineId;
        private final Id transitRouteId;
        private final Id departureId;
        private double passengers = 0;
        Id lastStop;

        // Constructors
        public PTVehicle(Id transitLineId, Id transitRouteId, Id departureId) {
            this.transitLineId = transitLineId;
            this.transitRouteId = transitRouteId;
            this.departureId = departureId;
        }

        public Id getDepartureId(){
            return this.departureId;
        }

        public Id getTransitLineId(){
            return this.transitLineId;
        }

        public Id getTransitLineRouteId(){
            return this.transitRouteId;
        }

        // Methods

        public double getPassengers(){
            return this.passengers;
        }

        public void addPassenger() {
            this.passengers += 1;
        }

        public void removePassenger() {
            this.passengers -= 1;
        }

        public void setLastStop(Id lastStop) {
            this.lastStop = lastStop;
        }

        public Id getLastStop(){
            return this.lastStop;
        }
    }
}
