package ch.sbb.matsim.utils;

import java.util.Set;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.CollectionUtils;

/**
 * @author jbischoff / SBB
 */
public class RemoveAgentRoutes {

	public static void main(String[] args) {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		String popWithRoutes = args[0];
		String popWithoutRoutes = args[1];
		Set<String> modesToRemoveRoutes = CollectionUtils.stringToSet(args[2]);
		removePtRoutes(modesToRemoveRoutes, scenario, popWithRoutes, popWithoutRoutes);
	}

	public static void removePtRoutes(Set<String> modesToRemoveRoutes, Scenario scenario, String popWithRoutes, String popWithoutRoutes) {
		SBBTripsToLegsAlgorithm algorithm = new SBBTripsToLegsAlgorithm(new SBBIntermodalAwareRouterModeIdentifier(scenario.getConfig()), modesToRemoveRoutes);
		StreamingPopulationReader streamingPopulationReader = new StreamingPopulationReader(scenario);
		StreamingPopulationWriter streamingPopulationWriter = new StreamingPopulationWriter();
		streamingPopulationWriter.startStreaming(popWithoutRoutes);
		streamingPopulationReader.addAlgorithm(person -> {
			PersonUtils.removeUnselectedPlans(person);
			for (Plan plan : person.getPlans()) {
				algorithm.run(plan);
			}
			streamingPopulationWriter.run(person);
		});
		streamingPopulationReader.readFile(popWithRoutes);
		streamingPopulationWriter.closeStreaming();
	}


}
