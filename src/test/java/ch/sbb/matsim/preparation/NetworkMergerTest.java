package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.variables.SBBModes;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.testcases.MatsimTestUtils;

public class NetworkMergerTest {
    @Rule
    public MatsimTestUtils utils = new MatsimTestUtils();

    @Test
    public void testNetworkMerger() {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Network network = scenario.getNetwork();

        NetworkFactory nf = network.getFactory();

        Node node1 = nf.createNode(Id.create(1, Node.class), new Coord(0, 0));
        Node node2 = nf.createNode(Id.create(2, Node.class), new Coord(15000, 0));
        Node node3 = nf.createNode(Id.create(3, Node.class), new Coord(25000, 0));
        Node node4 = nf.createNode(Id.create(4, Node.class), new Coord(35000, 0));
        Node node5 = nf.createNode(Id.create(5, Node.class), new Coord(40000, 0));

        network.addNode(node1);
        network.addNode(node2);
        network.addNode(node3);
        network.addNode(node4);
        network.addNode(node5);

        Link link1 = nf.createLink(Id.createLinkId("1"), node1, node2);
        link1.setAllowedModes(CollectionUtils.stringToSet(SBBModes.PT));
        Link link2 = nf.createLink(Id.createLinkId("2"), node2, node3);
        link2.setAllowedModes(CollectionUtils.stringToSet(SBBModes.CAR));
        Link link3 = nf.createLink(Id.createLinkId("3"), node3, node4);
        link3.setAllowedModes(CollectionUtils.stringToSet(SBBModes.RIDE));
        Link link4 = nf.createLink(Id.createLinkId("4"), node4, node5);
        link4.setAllowedModes(CollectionUtils.stringToSet(SBBModes.PT + "," + SBBModes.CAR));

        network.addLink(link1);
        network.addLink(link2);
        network.addLink(link3);
        network.addLink(link4);

        Network reducedNetwork = NetworkMerger.removePtOnlyLinks(network);

        Assert.assertEquals(reducedNetwork.getLinks().size(), 3);
    }
}
