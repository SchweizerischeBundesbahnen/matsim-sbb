/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis;

import ch.sbb.matsim.csv.CSVWriter;
import com.google.common.base.Functions;
import java.io.BufferedWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
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
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.io.UncheckedIOException;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;

public class PtVolumeToCSV implements TransitDriverStartsEventHandler,
		VehicleArrivesAtFacilityEventHandler,
		PersonEntersVehicleEventHandler,
		PersonLeavesVehicleEventHandler,
		VehicleDepartsAtFacilityEventHandler,
		EventsAnalysis {

	private static final Logger log = LogManager.getLogger(PtVolumeToCSV.class);

	private static final String FILENAME_STOPS = "matsim_stops.csv.gz";
	private static final String FILENAME_VEHJOURNEYS = "matsim_vehjourneys.csv.gz";
	private static final String FILENAME_FINALDAILYSTOPVOLUMES = "matsim_stops_daily.csv.gz";

	private static final String CSV_SEP = ";";
	private static final String COL_ITERATION = "it";
	private static final String COL_STOP_ID = "stop_id";
	private static final String COL_FROM_STOP_ID = "from_stop_id";
	private static final String COL_TO_STOP_ID = "to_stop_id";
	private static final String COL_INDEX = "index";
	private static final String COL_BOARDING = "boarding";
	private static final String COL_ALIGHTING = "alighting";
	private static final String COL_LINE = "line";
	private static final String COL_LINEROUTE = "lineroute";
    private static final String COL_DEPARTURE_ID = "departure_id";
    private static final String COL_VEHICLE_ID = "vehicle_id";
    private static final String COL_ARRIVAL = "arrival";
    private static final String COL_DEPARTURE = "departure";
    private static final String COL_PASSENGERS = "passengers";

    private static final String[] COLS_STOPS = new String[]{COL_INDEX, COL_STOP_ID, COL_BOARDING, COL_ALIGHTING, COL_LINE, COL_LINEROUTE, COL_DEPARTURE_ID, COL_VEHICLE_ID, COL_DEPARTURE, COL_ARRIVAL};
    private static final String[] COLS_VEHJOURNEYS = new String[]{COL_INDEX, COL_FROM_STOP_ID, COL_TO_STOP_ID, COL_PASSENGERS, COL_LINE, COL_LINEROUTE, COL_DEPARTURE_ID, COL_VEHICLE_ID, COL_DEPARTURE,
            COL_ARRIVAL};

    private final Map<Id, PTVehicle> ptVehicles = new HashMap<>();
    private final HashSet<Id> ptAgents = new HashSet<>();
    private CSVWriter stopsWriter = null;
    private CSVWriter vehJourneyWriter = null;
    private BufferedWriter stopsVolumesPerIterationWriter = null;

    private final String filename;
    private final boolean writeFinalDailyVolumes;
    private Map<Id<TransitStopFacility>, Integer> dailyStopVolumes = new LinkedHashMap<>();

    public PtVolumeToCSV(Scenario scenario, String filename, boolean writeFinalDailyVolumes) {
        this.filename = filename;
        this.writeFinalDailyVolumes = writeFinalDailyVolumes;
        if (!writeFinalDailyVolumes) {
            try {
                this.stopsWriter = new CSVWriter("", COLS_STOPS, this.filename + FILENAME_STOPS);
                this.vehJourneyWriter = new CSVWriter("", COLS_VEHJOURNEYS, this.filename + FILENAME_VEHJOURNEYS);
            } catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		} else {
			try {
				this.dailyStopVolumes = scenario.getTransitSchedule().getFacilities().keySet()
						.stream().collect(Collectors.toMap(Functions.identity(), i -> 0));
				this.stopsVolumesPerIterationWriter = IOUtils.getBufferedWriter(this.filename + FILENAME_FINALDAILYSTOPVOLUMES);
				this.stopsVolumesPerIterationWriter.write(COL_ITERATION);
				for (Id id : this.dailyStopVolumes.keySet()) {
					this.stopsVolumesPerIterationWriter.write(CSV_SEP);
					this.stopsVolumesPerIterationWriter.write(id.toString());
				}
				this.stopsVolumesPerIterationWriter.write(IOUtils.NATIVE_NEWLINE);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

	// Methods
	@Override
	public void reset(int iteration) {
		this.ptAgents.clear();
		this.ptVehicles.clear();
		if (this.stopsVolumesPerIterationWriter != null) {
			try {
				this.stopsVolumesPerIterationWriter.write(String.valueOf(iteration));
				for (int vol : this.dailyStopVolumes.values()) {
					this.stopsVolumesPerIterationWriter.write(CSV_SEP);
					this.stopsVolumesPerIterationWriter.write(String.valueOf(vol));
				}
				this.stopsVolumesPerIterationWriter.write(IOUtils.NATIVE_NEWLINE);
				this.stopsVolumesPerIterationWriter.flush();
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			this.dailyStopVolumes.replaceAll((k, v) -> 0);
		}
	}

	@Override
	public void handleEvent(TransitDriverStartsEvent event) {
		PTVehicle ptVehicle = new PTVehicle(event.getTransitLineId(), event.getTransitRouteId(), event.getDepartureId(), event.getVehicleId());
		this.ptVehicles.put(event.getVehicleId(), ptVehicle);
		this.ptAgents.add(event.getDriverId());
	}

	@Override
	public void handleEvent(VehicleDepartsAtFacilityEvent event) {
		Id<Vehicle> vId = event.getVehicleId();
		PTVehicle ptVehicle = this.ptVehicles.get(vId);
		if (ptVehicle != null) {
			ptVehicle.setDeparture(event.getTime(), event.getFacilityId());
		}
	}

	@Override
	public void handleEvent(VehicleArrivesAtFacilityEvent event) {
		Id<Vehicle> vId = event.getVehicleId();
		PTVehicle ptVehicle = this.ptVehicles.get(vId);
		if (ptVehicle != null) {
			ptVehicle.setArrival(event.getTime(), event.getFacilityId());
		}
	}

	@Override
	public void handleEvent(PersonEntersVehicleEvent event) {
		if (this.ptAgents.contains(event.getPersonId())) {
			return;
		}
		Id<Vehicle> vId = event.getVehicleId();
		PTVehicle ptVehicle = this.ptVehicles.get(vId);
		if (ptVehicle != null) {
			ptVehicle.addPassenger();
		}
	}

	@Override
	public void handleEvent(PersonLeavesVehicleEvent event) {
		if (this.ptAgents.contains(event.getPersonId())) {
			return;
		}
		Id<Vehicle> vId = event.getVehicleId();
		PTVehicle ptVehicle = this.ptVehicles.get(vId);
		if (ptVehicle != null) {
			ptVehicle.removePassenger();
		}
	}

	@Override
	public void writeResults(boolean lastIteration) {
		if (this.writeFinalDailyVolumes) {
			try {
				this.stopsVolumesPerIterationWriter.close();
			} catch (IOException e) {
				log.error("Could not close volumes per iteration file.", e);
			}
		} else {
			try {
				this.stopsWriter.close();
				this.vehJourneyWriter.close();
			} catch (IOException e) {
				log.error("Could not close files.", e);
			}
			if (lastIteration) {
				EventsAnalysis.copyToOutputFolder(this.filename, FILENAME_STOPS);
				EventsAnalysis.copyToOutputFolder(this.filename, FILENAME_VEHJOURNEYS);
			}
		}

	}

	// Private classes
	private class PTVehicle {

		// Attributes
		private final Id transitLineId;
		private final Id transitRouteId;
		private final Id departureId;
		private final Id vehicleId;
		private int passengers = 0;
		private int boardings = 0;
		private int alightings = 0;

		private double departure = 0;
		private double arrival = 0;
		private Id lastStop = null;
		private int indexVehJourney = 0;
		private int indexStops = 0;

		PTVehicle(Id transitLineId, Id transitRouteId, Id departureId, Id<Vehicle> vehicleId) {
			this.transitLineId = transitLineId;
			this.transitRouteId = transitRouteId;
			this.departureId = departureId;
			this.vehicleId = vehicleId;
		}

		void addPassenger() {
			this.boardings += 1;
			this.passengers += 1;
		}

		void removePassenger() {
			this.alightings += 1;
			this.passengers -= 1;
		}

		void setDeparture(double departure, Id<TransitStopFacility> stop) {
			this.departure = departure;

			if (PtVolumeToCSV.this.stopsWriter != null) {
				CSVWriter writer = PtVolumeToCSV.this.stopsWriter;
				writer.set(COL_INDEX, Integer.toString(this.indexStops));
				writer.set(COL_STOP_ID, stop.toString());
				writer.set(COL_BOARDING, Double.toString(this.boardings));
				writer.set(COL_ALIGHTING, Double.toString(this.alightings));
				writer.set(COL_LINE, this.transitLineId.toString());
				writer.set(COL_LINEROUTE, this.transitRouteId.toString());
				writer.set(COL_DEPARTURE_ID, this.departureId.toString());
				writer.set(COL_VEHICLE_ID, this.vehicleId.toString());
				writer.set(COL_DEPARTURE, Double.toString(this.departure));
				writer.set(COL_ARRIVAL, Double.toString(this.arrival));

				writer.writeRow();
			}
			if (PtVolumeToCSV.this.writeFinalDailyVolumes) {
				Integer currentItVol = PtVolumeToCSV.this.dailyStopVolumes.get(stop);
				PtVolumeToCSV.this.dailyStopVolumes.put(stop, currentItVol + this.alightings + this.boardings);
			}
			this.alightings = 0;
			this.boardings = 0;
			this.indexStops += 1;
		}

		void setArrival(double arrival, Id stop) {

			if (this.lastStop != null && PtVolumeToCSV.this.vehJourneyWriter != null) {
				CSVWriter writer = PtVolumeToCSV.this.vehJourneyWriter;

				writer.set(COL_INDEX, Integer.toString(this.indexVehJourney));
				writer.set(COL_FROM_STOP_ID, this.lastStop.toString());
				writer.set(COL_TO_STOP_ID, stop.toString());
				writer.set(COL_PASSENGERS, Double.toString(this.passengers));
				writer.set(COL_LINE, this.transitLineId.toString());
				writer.set(COL_LINEROUTE, this.transitRouteId.toString());
				writer.set(COL_DEPARTURE_ID, this.departureId.toString());
				writer.set(COL_VEHICLE_ID, this.vehicleId.toString());
				writer.set(COL_DEPARTURE, Double.toString(this.departure));
				writer.set(COL_ARRIVAL, Double.toString(arrival));

				writer.writeRow();
			}
			this.lastStop = stop;
			this.arrival = arrival;
			this.indexVehJourney += 1;
		}
	}
}
