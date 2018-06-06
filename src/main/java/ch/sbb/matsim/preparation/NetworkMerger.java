package ch.sbb.matsim.preparation;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.network.io.NetworkReaderMatsimV2;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.CollectionUtils;

public class NetworkMerger {
    public static void main(String[] args)  {
        String inputNetwork = args[0];
        String transitNetwork = args[1];
        String outputNetwork = args[2];

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        new NetworkReaderMatsimV2(scenario.getNetwork()).readFile(inputNetwork);

        new TransportModeNetworkFilter(scenario.getNetwork()).filter(scenario.getNetwork(),
                CollectionUtils.stringToSet(TransportMode.car + ","  +TransportMode.ride));

        new NetworkReaderMatsimV2(scenario.getNetwork()).readFile(transitNetwork);

        new NetworkWriter(scenario.getNetwork()).write(outputNetwork);
    }
}
