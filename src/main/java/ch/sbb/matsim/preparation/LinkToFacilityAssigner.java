package ch.sbb.matsim.preparation;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.facilities.ActivityFacilities;
import org.matsim.facilities.FacilitiesUtils;
import org.matsim.facilities.FacilitiesWriter;
import org.matsim.facilities.MatsimFacilitiesReader;
import sun.nio.ch.Net;

import java.util.Collections;

public class LinkToFacilityAssigner {

    private ActivityFacilities facilities;


    public static void main(String[] args) {
        String facilityFile = args[0];
        String networkFile = args[1];
        String outputFile = args[2];

        new LinkToFacilityAssigner().run(facilityFile, networkFile, outputFile);
    }

    public LinkToFacilityAssigner() {

    }

    public void run(String facilityFile, String networkFile, String output) {
        readFacilities(facilityFile);
        Network filteredNetwork = new FilteredNetwork().readAndFilterNetwork(networkFile);
        assignLinkToFacility(filteredNetwork);
        writeFacilityFile(output);
    }

    private void readFacilities(String facilityFile) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimFacilitiesReader(scenario).readFile(facilityFile);
        this.facilities = scenario.getActivityFacilities();
    }


    private void assignLinkToFacility(Network network) {
        this.facilities.getFacilities().values().
                forEach(f -> FacilitiesUtils.setLinkID(f, NetworkUtils.getNearestLink(network,
                        f.getCoord()).getId()));
    }

    private void writeFacilityFile(String output) {
        new FacilitiesWriter(this.facilities).write(output);
    }
}


