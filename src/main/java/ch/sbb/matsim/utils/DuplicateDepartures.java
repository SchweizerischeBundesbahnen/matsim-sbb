package ch.sbb.matsim.utils;

import ch.sbb.matsim.config.variables.SBBModes;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.Departure;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.vehicles.MatsimVehicleReader;
import org.matsim.vehicles.MatsimVehicleWriter;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DuplicateDepartures {

    public static void main(String[] args) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile("\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_4.0\\2020\\pt\\NPVM2020\\output\\transitSchedule.xml.gz");
        new MatsimVehicleReader(scenario.getTransitVehicles()).readFile("\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_4.0\\2020\\pt\\NPVM2020\\output\\transitVehicles.xml.gz");
        int i = 0;
        for (var line : scenario.getTransitSchedule().getTransitLines().values()) {
            for (var route : line.getRoutes().values()) {
                if (route.getTransportMode().equals(SBBModes.PTSubModes.BUS)) {
                    List<Double> departureTimes = route.getDepartures().values().stream().map(Departure::getDepartureTime).collect(Collectors.toList());
                    List<Double> additionalDepartures = new ArrayList<>();
                    Collections.sort(departureTimes);
                    double previous = 0;
                    VehicleType type = scenario.getTransitVehicles().getVehicles().get(route.getDepartures().values().stream().findFirst().get().getVehicleId()).getType();
                    for (double t : departureTimes) {
                        if (previous > 0) {
                            double departureToAdd = t - 0.5 * (t - previous);
                            additionalDepartures.add(departureToAdd);
                        }
                        previous = t;
                    }
                    for (double t : additionalDepartures) {
                        Departure departure = scenario.getTransitSchedule().getFactory().createDeparture(Id.create("add" + line + "_" + route + "_" + t, Departure.class), t);
                        Id<Vehicle> vehicleId = Id.create("addV_" + i, Vehicle.class);
                        Vehicle vehicle = scenario.getTransitVehicles().getFactory().createVehicle(vehicleId, type);
                        scenario.getTransitVehicles().addVehicle(vehicle);
                        route.addDeparture(departure);
                        i++;
                    }
                }
            }
        }
        System.out.println("added departures: " + i);
        new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile("c:\\devsbb\\transitSchedule.xml.gz");
        new MatsimVehicleWriter(scenario.getTransitVehicles()).writeFile("c:\\devsbb\\transitVehicles.xml.gz");
    }
}
