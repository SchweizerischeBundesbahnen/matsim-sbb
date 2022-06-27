package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.NetworkConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.Collections;

public class FilteredNetwork {

	private Network filteredNetwork;
	private NetworkFactory networkFactory;

	public Network readAndFilterNetwork(String networkFile) {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);

        final Network carNetwork = NetworkUtils.createNetwork(ConfigUtils.createConfig());
        new TransportModeNetworkFilter(scenario.getNetwork()).filter(carNetwork, Collections.singleton(SBBModes.CAR));

        this.filteredNetwork = NetworkUtils.createNetwork(ConfigUtils.createConfig());
        this.networkFactory = this.filteredNetwork.getFactory();

		carNetwork.getLinks().values().stream().
				filter(l -> l.getAttributes().getAttribute(Variables.ACCESS_CONTROLLED).toString().equals("0")).
				forEach(this::addLinkToNetwork);

        return this.filteredNetwork;
    }

	public Network filterNetwork(Network network) {
		NetworkConfigGroup ncg = new NetworkConfigGroup();
		ncg.setTimeVariantNetwork(true);
		final Network carNetwork = NetworkUtils.createNetwork(ncg);
		new TransportModeNetworkFilter(network).filter(carNetwork, Collections.singleton(SBBModes.CAR));

		this.filteredNetwork = NetworkUtils.createNetwork(ncg);
		this.networkFactory = this.filteredNetwork.getFactory();

		carNetwork.getLinks().values().stream().
				filter(l -> (!String.valueOf(l.getAttributes().getAttribute("accessControlled")).equals("1"))).
				forEach(this::addLinkToNetwork);

		return this.filteredNetwork;

    }

	private void addLinkToNetwork(Link link) {
		Node origFromNode = link.getFromNode();
		Node fromNode = addNodeIfNotExists(origFromNode);
		Node origToNode = link.getToNode();
		Node toNode = addNodeIfNotExists(origToNode);
		Link xy2lLink = this.networkFactory.createLink(link.getId(), fromNode, toNode);
		xy2lLink.setAllowedModes(link.getAllowedModes());
		xy2lLink.setCapacity(link.getCapacity());
		xy2lLink.setFreespeed(link.getFreespeed());
		xy2lLink.setLength(link.getLength());
		xy2lLink.setNumberOfLanes(link.getNumberOfLanes());
		this.filteredNetwork.addLink(xy2lLink);
	}

	private Node addNodeIfNotExists(Node node) {
		Node newNode = this.filteredNetwork.getNodes().get(node.getId());
		if (newNode == null) {
			newNode = this.networkFactory.createNode(node.getId(), node.getCoord());
			this.filteredNetwork.addNode(newNode);
		}
		return newNode;
	}

}