package ch.sbb.matsim.rideshare.utils;

import ch.sbb.matsim.config.variables.SBBModes;
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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class FleetGenerator {

	/**
	 * Adjust these variables and paths to your need.
	 */

	private static final int numberOfVehicles = 1000;
	private static final int seatsPerVehicle = 8;
	private static final double operationStartTime = 0.0;
	private static final double operationEndTime = 30d * 60d * 60d; //24h
	private static final Random random = MatsimRandom.getRandom();

	private static final Path networkFile = Paths.get("\\\\wsbbrz0283\\mobi\\40_Projekte\\20221221_Postauto_OnDemand\\20221221_Appenzell\\streets\\output\\network.xml.gz");
	private static final Path outputFile = Paths.get("\\\\wsbbrz0283\\mobi\\40_Projekte\\20221221_Postauto_OnDemand\\20221221_Appenzell\\sim\\1000veh\\fleetVehicles.xml");

	public static void main(String[] args) {

		new FleetGenerator().run();
	}

	private void run() {

		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile.toString());
		List<Id<Link>> availableLinks = scenario.getNetwork().getLinks().entrySet().stream()
				.filter(entry -> entry.getValue().getAllowedModes().contains(SBBModes.CAR)).map(e -> e.getKey()).collect(Collectors.toList());

		Set<DvrpVehicleSpecification> vehicleSpecifications = new HashSet<>();
		for (int z = 0; z < numberOfVehicles; z++) {
			double startTime = operationStartTime + random.nextInt(12) * 3600;
			double endTime = startTime + 16 * 3600;
			vehicleSpecifications.add(ImmutableDvrpVehicleSpecification.newBuilder()
					.id(Id.create("drt_" + z, DvrpVehicle.class))
					.startLinkId(availableLinks.get(random.nextInt(availableLinks.size())))
					.capacity(seatsPerVehicle)
					.serviceBeginTime(startTime)
					.serviceEndTime(endTime)
					.build());
		}
		new FleetWriter(vehicleSpecifications.stream().sorted(Comparator.comparing(v -> v.getId().toString()))).write(outputFile.toString());
	}
}
