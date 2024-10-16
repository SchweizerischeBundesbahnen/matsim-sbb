package ch.sbb.matsim.utils;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;
import org.matsim.vehicles.MatsimVehicleReader;
import org.matsim.vehicles.MatsimVehicleWriter;

public class MergeSchedules {

    public static void main(String[] args) {
        String schedule1 = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240722_Vivaldi_DRT\\pt\\viv-nolines\\output\\transitSchedule.xml.gz";
        String network1 = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240722_Vivaldi_DRT\\pt\\viv-nolines\\output\\transitNetwork.xml.gz";
        String vehicles1 = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240722_Vivaldi_DRT\\pt\\viv-nolines\\output\\transitVehicles.xml.gz";

        String schedule2 = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240722_Vivaldi_DRT\\pt\\neue-linien-7min-v2\\output-bus\\transitSchedule.xml";
        String network2 = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240722_Vivaldi_DRT\\pt\\neue-linien-7min-v2\\output-bus\\transitNetwork.xml";
        String vehicles2 = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240722_Vivaldi_DRT\\pt\\neue-linien-7min-v2\\output-bus\\transitVehicles.xml";

        String outputPath = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20240722_Vivaldi_DRT\\pt\\neue-linien-7min-v2\\output\\";

        Scenario scenario1 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimNetworkReader(scenario1.getNetwork()).readFile(network1);
        new TransitScheduleReader(scenario1).readFile(schedule1);
        new MatsimVehicleReader(scenario1.getTransitVehicles()).readFile(vehicles1);

        new MatsimNetworkReader(scenario1.getNetwork()).readFile(network2);
        new TransitScheduleReader(scenario1).readFile(schedule2);
        new MatsimVehicleReader(scenario1.getTransitVehicles()).readFile(vehicles2);

        new TransitScheduleWriter(scenario1.getTransitSchedule()).writeFile(outputPath + "transitSchedule.xml.gz");
        new MatsimVehicleWriter(scenario1.getTransitVehicles()).writeFile(outputPath + "transitVehicles.xml.gz");
        new NetworkWriter(scenario1.getNetwork()).write(outputPath + "transitNetwork.xml.gz");


    }
}
