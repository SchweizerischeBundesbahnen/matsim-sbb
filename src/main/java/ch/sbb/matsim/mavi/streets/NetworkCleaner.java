package ch.sbb.matsim.mavi.streets;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;

public class NetworkCleaner {

	public static void main(String[] args) {

		String input = args[0];
		String output = args[1];

		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new MatsimNetworkReader(scenario.getNetwork()).readFile(input);
		org.matsim.core.network.algorithms.NetworkCleaner cleaner = new org.matsim.core.network.algorithms.NetworkCleaner();
		cleaner.run(scenario.getNetwork());
		new NetworkWriter(scenario.getNetwork()).write(output);
	}
}