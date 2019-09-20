package ch.sbb.matsim.rideshare.utils;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.DvrpVehicleSpecification;
import org.matsim.contrib.dvrp.fleet.FleetWriter;
import org.matsim.contrib.dvrp.fleet.ImmutableDvrpVehicleSpecification;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.stream.Stream;

public class FleetGenerator {
    /**
     * Adjust these variables and paths to your need.
     */

    private static final int numberOfVehicles = 100;
    private static final int seatsPerVehicle = 2; //this is important for DRT, value is not used by taxi
    private static final double operationStartTime = 0;
    private static final double operationEndTime = 30 * 60 * 60; //24h
    private static final Random random = MatsimRandom.getRandom();

    private static final Path networkFile = Paths.get("C:\\devsbb\\data\\0.01_neuenburg\\input\\NE.100.output_network.xml.gz");
    private static final Path outputFile = Paths.get("C:\\devsbb\\data\\0.01_neuenburg\\input\\fleetVehicles.xml");

    public static void main(String[] args) {

        new FleetGenerator().run();
    }

    private void run() {

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile.toString());
        final int[] i = {0};
        Stream<DvrpVehicleSpecification> vehicleSpecificationStream = scenario.getNetwork().getLinks().entrySet().stream()
                .filter(entry -> entry.getValue().getAllowedModes().contains(TransportMode.car)) // drt can only start on links with Transport mode 'car'

                .sorted((e1, e2) -> (random.nextInt(2) - 1)) // shuffle links
                .limit(numberOfVehicles) // select the first *numberOfVehicles* links
                .map(entry -> ImmutableDvrpVehicleSpecification.newBuilder()
                        .id(Id.create("drt_" + i[0]++, DvrpVehicle.class))
                        .startLinkId(entry.getKey())
                        .capacity(seatsPerVehicle)
                        .serviceBeginTime(operationStartTime)
                        .serviceEndTime(operationEndTime)
                        .build());

        new FleetWriter(vehicleSpecificationStream).write(outputFile.toString());
    }
}
