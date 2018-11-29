package ch.sbb.matsim.preparation;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.CollectionUtils;

public class NetworkMerger {
    public static void main(String[] args)  {
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
        Network reducedNetwork = NetworkUtils.createNetwork();
        new TransportModeNetworkFilter(network).filter(reducedNetwork,
                CollectionUtils.stringToSet(TransportMode.car + ","  +TransportMode.ride));
        return reducedNetwork;
    }
}
