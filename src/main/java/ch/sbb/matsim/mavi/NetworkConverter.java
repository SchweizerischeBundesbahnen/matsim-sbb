package ch.sbb.matsim.mavi;

import ch.sbb.matsim.analysis.LinkAnalyser.VisumNetwork.VisumNetwork;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.Collections;

public class NetworkConverter {
    public NetworkConverter(Network network) {
        VisumNetwork visumNetwork = new VisumNetwork();

        for(Link link: network.getLinks().values()){
            visumNetwork.getOrCreateLink(link);
        }

        visumNetwork.write("D:\\tmp\\");
    }

    public static void main(String[] args) {
        Config config = ConfigUtils.createConfig();
        config.network().setInputFile(args[0]);

        Scenario scenario = ScenarioUtils.loadScenario(config);
        Network network = NetworkUtils.createNetwork();
        new TransportModeNetworkFilter(scenario.getNetwork()).filter(network, Collections.singleton(TransportMode.car));

        scenario = null;
        System.gc();
        System.gc();
        System.gc();
        new NetworkConverter(network);
    }

}
