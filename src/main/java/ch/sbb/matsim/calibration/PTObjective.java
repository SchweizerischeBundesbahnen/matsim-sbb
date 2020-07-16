/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.calibration;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
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

public class PTObjective implements TransitDriverStartsEventHandler,
		VehicleArrivesAtFacilityEventHandler,
		PersonEntersVehicleEventHandler,
		PersonLeavesVehicleEventHandler, VehicleDepartsAtFacilityEventHandler {

	private final static Logger log = Logger.getLogger(PTObjective.class);

	private CSVWriter csvWriter;

	private Map<Id, PTVehicle> ptVehicles = new HashMap<>();
	private HashSet<Id> ptAgents = new HashSet<>();
	private HashMap<Set<String>, Float> visumData = new HashMap<>();
	private double score = 0.0;

	PTObjective(final String csvVolumesFilename, String outputFilename) throws IOException {
		this.csvWriter = new CSVWriter("", new String[]{"line", "lineRoute", "departureId", "facilityId", "passengers", "visum_passengers"}, outputFilename);

		try (CSVReader visumVolume = new CSVReader(new String[]{"line", "lineRoute", "departureId", "facilityId", "passengers"}, csvVolumesFilename, ",")) {
			Map<String, String> row;
			while ((row = visumVolume.readLine()) != null) {
				Set<String> key = new HashSet<>();
				key.add(row.get("line"));
				key.add(row.get("lineRoute"));
				key.add(row.get("departureId"));
				key.add(row.get("facilityId"));
				this.visumData.put(key, Float.parseFloat(row.get("passengers")));
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
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
		if (ptVehicles.containsKey(vId)) {
			PTVehicle ptVehicle = ptVehicles.get(vId);
			ptVehicle.setLastStop(event.getFacilityId());

			String line = ptVehicle.getTransitLineId().toString();
			String lineRoute = ptVehicle.getTransitLineRouteId().toString();
			String departureId = ptVehicle.getDepartureId().toString();
			String facilityId = event.getFacilityId().toString();
			double pax = ptVehicle.getPassengers();
			String passengers = String.valueOf(ptVehicle.getPassengers());

			this.csvWriter.set("line", line);
			this.csvWriter.set("lineRoute", lineRoute);
			this.csvWriter.set("departureId", departureId);
			this.csvWriter.set("facilityId", facilityId);
			this.csvWriter.set("passengers", passengers);

			Set<String> key = new HashSet<>();
			key.add(line);
			key.add(lineRoute);
			key.add(departureId);
			key.add(facilityId);
			double visumPax = this.visumData.get(key).doubleValue();
			this.csvWriter.set("visum_passengers", String.valueOf(this.visumData.get(key)));

			this.score += Math.pow(pax - visumPax, 2);

			this.csvWriter.writeRow();
		}
	}

	@Override
	public void handleEvent(VehicleArrivesAtFacilityEvent event) {
		Id<Vehicle> vId = event.getVehicleId();
		if (ptVehicles.containsKey(vId)) {
			PTVehicle ptVehicle = ptVehicles.get(vId);
			ptVehicle.setLastStop(event.getFacilityId());
		}
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		Id<Vehicle> vId = event.getVehicleId();

		if (ptAgents.contains(event.getPersonId())) {
			return;
		}

		if (ptVehicles.containsKey(vId)) {
			PTVehicle ptVehicle = ptVehicles.get(vId);
			ptVehicle.addPassenger();
		}
	}

	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {
		if (ptAgents.contains(event.getPersonId())) {
			return;
		}
		Id<Vehicle> vId = event.getVehicleId();
		if (ptVehicles.containsKey(vId)) {
			PTVehicle ptVehicle = ptVehicles.get(vId);
			ptVehicle.removePassenger();
		}
	}

	// Methods
	@Override
	public void reset(int iteration) {
		this.score = 0.0;
	}

	double getScore() {
		return this.score;
	}

	public void close() {
		try {
			this.csvWriter.close();
		} catch (IOException e) {
			log.error("Could not close file. " + e.getMessage(), e);
		}
	}

	// Private classes
	private class PTVehicle {

		// Attributes
		private final Id transitLineId;
		private final Id transitRouteId;
		private final Id departureId;
		Id lastStop;
		private double passengers = 0;

		// Constructors
		PTVehicle(Id transitLineId, Id transitRouteId, Id departureId) {
			this.transitLineId = transitLineId;
			this.transitRouteId = transitRouteId;
			this.departureId = departureId;
		}

		Id getDepartureId() {
			return this.departureId;
		}

		Id getTransitLineId() {
			return this.transitLineId;
		}

		Id getTransitLineRouteId() {
			return this.transitRouteId;
		}

		// Methods

		double getPassengers() {
			return this.passengers;
		}

		void addPassenger() {
			this.passengers += 1;
		}

		void removePassenger() {
			this.passengers -= 1;
		}

		public Id getLastStop() {
			return this.lastStop;
		}

		void setLastStop(Id lastStop) {
			this.lastStop = lastStop;
		}
	}
}
