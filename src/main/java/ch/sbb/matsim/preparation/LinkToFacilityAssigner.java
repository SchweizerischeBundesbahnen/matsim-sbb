package ch.sbb.matsim.preparation;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.FacilitiesUtils;
import org.matsim.facilities.FacilitiesWriter;
import org.matsim.facilities.MatsimFacilitiesReader;

public class LinkToFacilityAssigner {

	public static void main(String[] args) {
        String facilityFile = args[0];
        String networkFile = args[1];
        String outputFile = args[2];
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimFacilitiesReader(scenario).readFile(facilityFile);
        Network network = NetworkUtils.createNetwork();
        new MatsimNetworkReader(network).readFile(networkFile);
        Network filteredNetwork = new FilteredNetwork().filterNetwork(network, scenario.getConfig());
        run(scenario.getActivityFacilities(), filteredNetwork, scenario.getConfig());
        new FacilitiesWriter(scenario.getActivityFacilities()).write(outputFile);

    }

	public static void run(ActivityFacilities facilities, Network network, Config config) {
		Network filteredNetwork = new FilteredNetwork().filterNetwork(network, config);
		assignLinkToFacility(facilities, filteredNetwork);

	}

	private static void assignLinkToFacility(ActivityFacilities facilities, Network network) {
		facilities.getFacilities().values().
				forEach(f -> FacilitiesUtils.setLinkID(f, NetworkUtils.getNearestLink(network,
						f.getCoord()).getId()));
	}

}


