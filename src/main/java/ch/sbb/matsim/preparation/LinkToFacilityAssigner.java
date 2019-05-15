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

import java.util.Collections;

public class LinkToFacilityAssigner {

    private ActivityFacilities facilities;
    private Network filteredNetwork;
    private NetworkFactory networkFactory;

    public static void main(String[] args)  {
        String facilityFile = args[0];
        String networkFile = args[1];
        String outputFile = args[2];

        new LinkToFacilityAssigner().run(facilityFile, networkFile, outputFile);
    }

    public LinkToFacilityAssigner() {

    }

    public void run(String facilityFile, String networkFile, String output) {
        readFacilities(facilityFile);
        readAndFilterNetwork(networkFile);
        assignLinkToFacility();
        writeFacilityFile(output);
    }

    private void readFacilities(String facilityFile)   {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimFacilitiesReader(scenario).readFile(facilityFile);
        this.facilities = scenario.getActivityFacilities();
    }

    private void readAndFilterNetwork(String networkFile) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);

        final Network carNetwork = NetworkUtils.createNetwork();
        new TransportModeNetworkFilter(scenario.getNetwork()).filter(carNetwork, Collections.singleton(TransportMode.car));

        this.filteredNetwork = NetworkUtils.createNetwork();
        this.networkFactory = this.filteredNetwork.getFactory();

        carNetwork.getLinks().values().stream().
                filter(l -> l.getAttributes().getAttribute("accessControlled").toString().equals("0")).
                forEach(this::addLinkToNetwork);
    }

    private void addLinkToNetwork(Link link)  {
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

    private Node addNodeIfNotExists(Node node)  {
        Node newNode = this.filteredNetwork.getNodes().get(node.getId());
        if (newNode == null) {
            newNode = this.networkFactory.createNode(node.getId(), node.getCoord());
            this.filteredNetwork.addNode(newNode);
        }
        return newNode;
    }

    private void assignLinkToFacility()  {
        this.facilities.getFacilities().values().
                forEach(f -> FacilitiesUtils.setLinkID(f, NetworkUtils.getNearestLink(this.filteredNetwork,
                        f.getCoord()).getId()));
    }

    private void writeFacilityFile(String output)    {
        new FacilitiesWriter(this.facilities).write(output);
    }
}
