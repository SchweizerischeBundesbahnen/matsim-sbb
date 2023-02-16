package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.variables.SBBModes;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.Config;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;

import java.util.Collections;

public class FilteredNetwork {

	private Network filteredNetwork;
	private NetworkFactory networkFactory;



	public Network filterNetwork(Network network, Config config) {

        final Network carNetwork = NetworkUtils.createNetwork(config);
        new TransportModeNetworkFilter(network).filter(carNetwork, Collections.singleton(SBBModes.CAR));

        this.filteredNetwork = NetworkUtils.createNetwork(config);
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