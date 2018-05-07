package ch.sbb.matsim.mavi;

import ch.sbb.matsim.analysis.LinkAnalyser.VisumNetwork.VisumLink;
import ch.sbb.matsim.analysis.LinkAnalyser.VisumNetwork.VisumNetwork;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.Collections;

public class NetworkConverter {
    public NetworkConverter(Network originalNetwork, Network network) {
        VisumNetwork visumNetwork = new VisumNetwork();

        for(Link link: network.getLinks().values()){
            VisumLink visumLink = visumNetwork.getOrCreateLink(link);
            Link originalLink = originalNetwork.getLinks().get(link.getId());
            originalLink.getAttributes().putAttribute("visumId", visumLink.getId()+"_"+visumLink.getFromNode().getId()+"_"+visumLink.getToNode().getId());
        }

        visumNetwork.write("D:\\tmp\\");
    }

    public static void main(String[] args) {
        Config config = ConfigUtils.createConfig();
        config.network().setInputFile("\\\\V00925\\Simba\\20_Modelle\\80_MatSim\\30_ModellCH\\01_ModellCH_15\\10_Network\\network_v3\\network_v3.xml.gz");

        Scenario scenario = ScenarioUtils.loadScenario(config);
        Network network = NetworkUtils.createNetwork();
        new TransportModeNetworkFilter(scenario.getNetwork()).filter(network, Collections.singleton(TransportMode.car));

        new NetworkConverter(scenario.getNetwork(), network);
        new NetworkWriter(scenario.getNetwork()).write("\\\\V00925\\Simba\\20_Modelle\\80_MatSim\\30_ModellCH\\01_ModellCH_15\\10_Network\\network_v3\\network_v3_visumIds.xml.gz");
    }

}
