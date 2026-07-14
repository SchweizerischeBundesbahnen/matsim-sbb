package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.variables.SBBModes;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class TransitSchedulePreparationTest {

    @Test
    void setRoutedModesToDifferentModes() {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		Network network = scenario.getNetwork();

		Node node1 = network.getFactory().createNode(Id.createNodeId("1"), new Coord(0, 0));
		Node node2 = network.getFactory().createNode(Id.createNodeId("2"), new Coord(100, 100));
		network.addNode(node1);
		network.addNode(node2);

		// Link 1: RAIL and CAR (One PT mode to map)
		Link link1 = network.getFactory().createLink(Id.createLinkId("1"), node1, node2);
		link1.setAllowedModes(Set.of(SBBModes.PTSubModes.RAIL, SBBModes.CAR));
		network.addLink(link1); // Important: must add to network so the loop finds it!

		// Link 2: BUS, TRAM, and BIKE (Multiple PT modes to map on the same link)
		Link link2 = network.getFactory().createLink(Id.createLinkId("2"), node1, node2);
		link2.setAllowedModes(Set.of(SBBModes.PTSubModes.BUS, SBBModes.PTSubModes.TRAM, "bike"));
		network.addLink(link2);

		// Link 3: CAR and WALK (No PT modes, should remain untouched)
		Link link3 = network.getFactory().createLink(Id.createLinkId("3"), node1, node2);
		link3.setAllowedModes(Set.of(SBBModes.CAR, "walk"));
		network.addLink(link3);

		TransitSchedulePreparation.setRoutedModesToDifferentModes(scenario);
		// --- 3. ASSERT ---
		// Validate Link 1
		Set<String> link1Modes = link1.getAllowedModes();
		assertTrue(link1Modes.contains(SBBModes.PTSubModes.RAIL + "_route"));
		assertTrue(link1Modes.contains(SBBModes.CAR));
		assertFalse(link1Modes.contains(SBBModes.PTSubModes.RAIL));
		assertEquals(2, link1Modes.size(), "Link1 should have exactly 2 modes");

		// Validate Link 2
		Set<String> link2Modes = link2.getAllowedModes();
		assertTrue(link2Modes.contains(SBBModes.PTSubModes.BUS + "_route"));
		assertTrue(link2Modes.contains(SBBModes.PTSubModes.TRAM + "_route"));
		assertTrue(link2Modes.contains("bike"));
		assertFalse(link2Modes.contains(SBBModes.PTSubModes.BUS));
		assertFalse(link2Modes.contains(SBBModes.PTSubModes.TRAM));
		assertEquals(3, link2Modes.size(), "Link2 should have exactly 3 modes");

		// Validate Link 3
		Set<String> link3Modes = link3.getAllowedModes();
		assertTrue(link3Modes.contains(SBBModes.CAR));
		assertTrue(link3Modes.contains("walk"));
		assertEquals(2, link3Modes.size(), "Link3 should have exactly 2 modes and remain untouched");

    }
}
