package ch.sbb.matsim.rerouting;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

public class TestInput {

    static String transitFile = "Z:/99_Playgrounds/MD/Umlegung/smallInput/smalltransitSchedule.xml.gz";
    static List<TransitStopFacility> trainStations = new ArrayList<>();
    static String output = "C:/devsbb/writeFilePlace/Umlegung/Nachfrage/Test_Nachfrage_";
    static Random random = new Random(1);

    public static void main(String[] args) {

        createTestInput();

    }

    private static void createTestInput() {

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(transitFile);

        for (TransitStopFacility transitStopFacility : scenario.getTransitSchedule().getFacilities().values()) {
            if (transitStopFacility.getAttributes().getAttribute("03_Stop_Code") != null) {
                trainStations.add(transitStopFacility);
            } else {
                System.out.println(transitStopFacility.getId().toString());
            }
        }

        for (int i = 10; i <= 24 * 60; i += 10) {
            createDemand(i);
        }

    }

    private static void createDemand(int i) {
        System.out.println("Writing demand " + i);
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(output + i + ".csv"))) {
            writer.write("Nachfrage");
            for (TransitStopFacility transitStopFacility : trainStations) {
                writer.write("," + transitStopFacility.getId().toString());
            }
            writer.newLine();
            writer.flush();
            for (TransitStopFacility transitStopFacility : trainStations) {
                writer.write(transitStopFacility.getId().toString());
                for (TransitStopFacility transitStopFacilityZiel : trainStations) {
                    if (transitStopFacility.equals(transitStopFacilityZiel)) {
                        writer.write(",-");
                    }
                    writer.write("," + (int) (random.nextDouble() * 100));
                }
                writer.newLine();
                writer.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
