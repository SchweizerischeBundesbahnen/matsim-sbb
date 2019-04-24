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

        LinkToFacilityAssigner assigner = new LinkToFacilityAssigner();
        assigner.readFacilities(facilityFile);
        assigner.readAndFilterNetwork(networkFile);
        assigner.assignLinkToFacility();
        assigner.writeFacilityFile(outputFile);
    }

    public LinkToFacilityAssigner() {

    }

    public void readFacilities(String facilityFile)   {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimFacilitiesReader(scenario).readFile(facilityFile);
        this.facilities = scenario.getActivityFacilities();
    }

    public void readAndFilterNetwork(String networkFile) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFile);

        final Network carNetwork = NetworkUtils.createNetwork();
        new TransportModeNetworkFilter(scenario.getNetwork()).filter(carNetwork, Collections.singleton(TransportMode.car));

        this.filteredNetwork = NetworkUtils.createNetwork();
        this.networkFactory = this.filteredNetwork.getFactory();

        carNetwork.getLinks().values().
                stream().
                filter(l -> l.getAttributes().getAttribute("accessControlled").toString().equals("0")).
                forEach(this::addLinkToNetwork);
    }

    private void addLinkToNetwork(Link link)  {
        Node fromNode = link.getFromNode();
        Node xy2lFromNode = this.filteredNetwork.getNodes().get(fromNode.getId());
        if (xy2lFromNode == null) {
            xy2lFromNode = this.networkFactory.createNode(fromNode.getId(), fromNode.getCoord());
            this.filteredNetwork.addNode(xy2lFromNode);
        }
        Node toNode = link.getToNode();
        Node xy2lToNode = this.filteredNetwork.getNodes().get(toNode.getId());
        if (xy2lToNode == null) {
            xy2lToNode = this.networkFactory.createNode(toNode.getId(), toNode.getCoord());
            this.filteredNetwork.addNode(xy2lToNode);
        }
        Link xy2lLink = this.networkFactory.createLink(link.getId(), xy2lFromNode, xy2lToNode);
        xy2lLink.setAllowedModes(link.getAllowedModes());
        xy2lLink.setCapacity(link.getCapacity());
        xy2lLink.setFreespeed(link.getFreespeed());
        xy2lLink.setLength(link.getLength());
        xy2lLink.setNumberOfLanes(link.getNumberOfLanes());
        this.filteredNetwork.addLink(xy2lLink);
    }

    public void assignLinkToFacility()  {
        this.facilities.getFacilities().values().
                forEach(f -> FacilitiesUtils.setLinkID(f, NetworkUtils.getNearestLink(this.filteredNetwork,
                        f.getCoord()).getId()));
    }

    public void writeFacilityFile(String output)    {
        new FacilitiesWriter(this.facilities).write(output);
    }
}
