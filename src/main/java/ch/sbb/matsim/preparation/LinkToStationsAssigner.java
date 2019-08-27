
package ch.sbb.matsim.preparation;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;


public class LinkToStationsAssigner {

    private Scenario scenario;


    public static void main(String[] args) {
        String transitSchedule = args[0];
        String networkFile = args[1];
        String outputFile = args[2];

        new LinkToStationsAssigner().run(transitSchedule, networkFile, outputFile);
    }


    public void run(String transitSchedule, String networkFile, String output) {
        readStations(transitSchedule);
        Network filteredNetwork = new FilteredNetwork().readAndFilterNetwork(networkFile);
        assignLinkToFacility(filteredNetwork);
        writeFacilityFile(output);
    }

    private void readStations(String transitSchedule) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new TransitScheduleReader(scenario).readFile(transitSchedule);
        this.scenario = scenario;
    }


    private void assignLinkToFacility(Network network) {
        this.scenario.getTransitSchedule().getFacilities().values().
                forEach(f -> f.getAttributes().putAttribute("accessLinkId", NetworkUtils.getNearestLink(network,
                        f.getCoord()).getId().toString()));
    }

    private void writeFacilityFile(String output) {
        new TransitScheduleWriter(scenario.getTransitSchedule()).writeFile(output);
    }
}