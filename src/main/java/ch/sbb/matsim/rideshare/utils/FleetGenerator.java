package ch.sbb.matsim.rideshare.utils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.contrib.dvrp.fleet.DvrpVehicle;
import org.matsim.contrib.dvrp.fleet.DvrpVehicleSpecification;
import org.matsim.contrib.dvrp.fleet.FleetWriter;
import org.matsim.contrib.dvrp.fleet.ImmutableDvrpVehicleSpecification;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;

public class FleetGenerator {
    /**
     * Adjust these variables and paths to your need.
     */

    private static final int numberOfVehicles = 100;
    private static final int seatsPerVehicle = 2; //this is important for DRT, value is not used by taxi
    private static final double operationStartTime = 0;
    private static final double operationEndTime = 30 * 60 * 60; //24h
    private static final Random random = MatsimRandom.getRandom();

    private static final Path networkFile = Paths.get("\\\\k13536\\mobi\\40_Projekte\\20190913_Ridesharing\\sim\\neuchatel\\input\\network.xml.gz");
    private static final Path outputFile = Paths.get("\\\\k13536\\mobi\\40_Projekte\\20190913_Ridesharing\\sim\\neuchatel\\input\\fleetVehicles_feeder_ne.xml");

    public static void main(String[] args) {

        new FleetGenerator().run();
    }

    private void run() {

		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile.toString());
		//        List<Id<Link>> availableLinks  = scenario.getNetwork().getLinks().entrySet().stream()
		//              .filter(entry -> entry.getValue().getAllowedModes().contains(SBBModes.CAR)).map(e->e.getKey()).collect(Collectors.toList());
		//        List<Id<Link>> availableLinks = Arrays.asList(Id.createLinkId(177382)); //lcf
		List<Id<Link>> availableLinks = Arrays.asList(Id.createLinkId(598836)); //ne
		Set<DvrpVehicleSpecification> vehicleSpecifications = new HashSet<>();
		for (int z = 0; z < numberOfVehicles; z++) {
			vehicleSpecifications.add(ImmutableDvrpVehicleSpecification.newBuilder()
					.id(Id.create("drt_" + z, DvrpVehicle.class))
					.startLinkId(availableLinks.get(random.nextInt(availableLinks.size())))
					.capacity(seatsPerVehicle)
					.serviceBeginTime(operationStartTime)
					.serviceEndTime(operationEndTime)
					.build());
        }
        new FleetWriter(vehicleSpecifications.stream().sorted(Comparator.comparing(v -> v.getId().toString()))).write(outputFile.toString());
    }
}
