package ch.sbb.matsim.preparation;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.io.NetworkReaderMatsimV2;
import org.matsim.core.network.io.NetworkWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.ArrayList;
import java.util.List;

public class NetworkMerger {
    public static void main(String[] args)  {
        String inputNetwork = args[0];
        String transitNetwork = args[1];
        String outputNetwork = args[2];

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new NetworkReaderMatsimV2(scenario.getNetwork()).readFile(inputNetwork);

        List<Link> linksToRemove = new ArrayList<>();

        for(Link link : scenario.getNetwork().getLinks().values())  {
            if(!link.getAllowedModes().contains(TransportMode.car) && !link.getAllowedModes().contains(TransportMode.ride))
                linksToRemove.add(link);
        }
        System.out.println("Removed " + linksToRemove.size() + " links.");
        for(Link link : linksToRemove)
            scenario.getNetwork().removeLink(link.getId());

        new NetworkReaderMatsimV2(scenario.getNetwork()).readFile(transitNetwork);

        new NetworkWriter(scenario.getNetwork()).write(outputNetwork);
    }
}
