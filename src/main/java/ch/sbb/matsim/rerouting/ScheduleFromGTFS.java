package ch.sbb.matsim.rerouting;

import java.time.LocalDate;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.NetworkWriter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.vehicles.MatsimVehicleWriter;

public class ScheduleFromGTFS {

    public static void main(String[] args) {

        String gtfsZipFile = "";
        CoordinateTransformation ct = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, "EPSG:25833");
        LocalDate date = LocalDate.parse("2020-06-25");

        //output files
        String scheduleFile = "transitSchedule.xml.gz";
        String networkFile = "network.xml.gz";
        String transitVehiclesFile ="transitVehicles.xml.gz";
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        //Convert GTFS
        //RunGTFS2MATSim.convertGTFSandAddToScenario(scenario,gtfsZipFile,date,ct,true,true);

        //Write out network, vehicles and schedule
        new NetworkWriter(scenario.getNetwork()).write(networkFile);
        new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(scheduleFile);
        new MatsimVehicleWriter(scenario.getTransitVehicles()).writeFile(transitVehiclesFile);

    }

}
