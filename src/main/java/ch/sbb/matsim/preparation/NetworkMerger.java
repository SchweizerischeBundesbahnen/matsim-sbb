package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.SBBSupplyConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.SBBModes.PTSubModes;
import java.net.URL;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.CollectionUtils;

public class NetworkMerger {

	public static void main(String[] args) {
		String inputNetwork = args[0];
		String transitNetwork = args[1];
		String outputNetwork = args[2];

		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new MatsimNetworkReader(scenario.getNetwork()).readFile(inputNetwork);

		Network reducedNetwork = removePtOnlyLinks(scenario.getNetwork());

		new MatsimNetworkReader(reducedNetwork).readFile(transitNetwork);
		new NetworkWriter(reducedNetwork).write(outputNetwork);
	}

	public static Network removePtOnlyLinks(Network network) {
        Network reducedNetwork = NetworkUtils.createNetwork(ConfigUtils.createConfig());
        new TransportModeNetworkFilter(network).filter(reducedNetwork,
                CollectionUtils.stringToSet(SBBModes.CAR + "," + SBBModes.RIDE));
        return reducedNetwork;
    }

	public static void mergeTransitNetworkFromSupplyConfig(Scenario scenario) {
		SBBSupplyConfigGroup sbbSupplyConfigGroup = ConfigUtils.addOrGetModule(scenario.getConfig(), SBBSupplyConfigGroup.class);
		if (sbbSupplyConfigGroup.getTransitNetworkFileString() != null) {
			URL transitNetworkFile = sbbSupplyConfigGroup.getTransitNetworkFile(scenario.getConfig().getContext());
			if (sbbSupplyConfigGroup.isCheckIfTransitNetworkExistsAlready()) {
				boolean hasPt = scenario.getNetwork().getLinks().values().stream().anyMatch(l -> PTSubModes.submodes.stream().anyMatch(m -> l.getAllowedModes().contains(m)));
				if (hasPt) {
					throw new RuntimeException("Street Network already contains pt links. Use streets only network or overwrite setting in config");
				}
			}
            // This call does not replace the original network, but additively reads the second.
			new MatsimNetworkReader(scenario.getNetwork()).readURL(transitNetworkFile);

		}
	}
}
