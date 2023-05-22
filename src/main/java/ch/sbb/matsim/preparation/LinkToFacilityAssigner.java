package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.NetworkConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.filter.NetworkFilterManager;
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
        run(scenario.getActivityFacilities(), network, scenario.getConfig());
        new FacilitiesWriter(scenario.getActivityFacilities()).write(outputFile);

    }

	public static void run(ActivityFacilities facilities, Network network, Config config) {
        Network filteredNetwork = getAccessibleLinks(network, config.network());
        assignLinkToFacility(facilities, filteredNetwork);

    }

    private static void assignLinkToFacility(ActivityFacilities facilities, Network network) {
        facilities.getFacilities().values().
                forEach(f -> FacilitiesUtils.setLinkID(f, NetworkUtils.getNearestLink(network,
                        f.getCoord()).getId()));
    }


    public static Network getAccessibleLinks(Network network, NetworkConfigGroup networkConfigGroup) {
        NetworkFilterManager networkFilterManager = new NetworkFilterManager(network, networkConfigGroup);
        networkFilterManager.addLinkFilter(l -> (!String.valueOf(l.getAttributes().getAttribute(Variables.ACCESS_CONTROLLED)).equals("1")));
        networkFilterManager.addLinkFilter(l -> l.getAllowedModes().contains(SBBModes.CAR));
        return networkFilterManager.applyFilters();
    }


}


