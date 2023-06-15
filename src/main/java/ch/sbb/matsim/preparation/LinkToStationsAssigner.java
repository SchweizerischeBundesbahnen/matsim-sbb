package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.variables.Variables;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Network;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.NetworkConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitScheduleWriter;

public class LinkToStationsAssigner {


	public static void main(String[] args) {
		String transitScheduleFile = args[0];
		String networkFile = args[1];
		String outputFile = args[2];

		Network network = NetworkUtils.createNetwork();
		new MatsimNetworkReader(network).readFile(networkFile);
		Network filteredNetwork = LinkToFacilityAssigner.getAccessibleLinks(network, new NetworkConfigGroup());
		final TransitSchedule transitSchedule = readStations(transitScheduleFile);
		assignLinkToFacility(filteredNetwork, transitSchedule);
		new TransitScheduleWriter(transitSchedule).writeFile(outputFile);
	}

	public static void runAssignment(Scenario scenario) {
		Network filteredNetwork = LinkToFacilityAssigner.getAccessibleLinks(scenario.getNetwork(), scenario.getConfig().network());
		assignLinkToFacility(filteredNetwork, scenario.getTransitSchedule());
	}

	private static TransitSchedule readStations(String transitSchedule) {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new TransitScheduleReader(scenario).readFile(transitSchedule);
		return scenario.getTransitSchedule();
	}

	private static void assignLinkToFacility(Network network, TransitSchedule transitSchedule) {
		transitSchedule.getFacilities().values().
				forEach(f -> f.getAttributes().putAttribute(Variables.INTERMODAL_ACCESS_LINK_ID, NetworkUtils.getNearestLink(network,
						f.getCoord()).getId().toString()));
	}

}