package ch.sbb.matsim.mavi.streets;

import ch.sbb.matsim.config.variables.SBBModes;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.algorithms.MultimodalNetworkCleaner;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.Set;

public class SBBMultiModalNetworkCleaner {

	public static void main(String[] args) {

		String input = args[0];
		String output = args[1];

		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new MatsimNetworkReader(scenario.getNetwork()).readFile(input);
		MultimodalNetworkCleaner cleaner = new MultimodalNetworkCleaner(scenario.getNetwork());
		cleaner.run(Set.of(SBBModes.CAR, SBBModes.RIDE, SBBModes.BIKE));
		StreetNetworkExporter.finalMultimodalCleanup(scenario.getNetwork());
		new NetworkWriter(scenario.getNetwork()).write(output);
	}
}