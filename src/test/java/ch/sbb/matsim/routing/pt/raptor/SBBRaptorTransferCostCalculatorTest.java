package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.config.SBBAccessTimeConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.routing.SBBAnalysisMainModeIdentifier;
import ch.sbb.matsim.routing.access.AccessEgressModule;
import ch.sbb.matsim.routing.network.SBBNetworkRoutingModule;
import ch.sbb.matsim.zones.ZonesModule;
import junit.framework.TestCase;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.router.MainModeIdentifier;
import org.matsim.core.scenario.ScenarioUtils;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorCore.PathElement;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SBBRaptorTransferCostCalculatorTest extends TestCase {

	SwissRailRaptorCore c;
	public void testCalcTransferCost() {
	}

	public void testGetFirstPEOfTripPart() {
	}
	
	public void testFixture(String mode, int start) {

		Config config = ConfigUtils.createConfig(new SBBAccessTimeConfigGroup());
		config.controller().setOutputDirectory("test/output/AccessTimeIntegrationTest");
		Scenario scenario = ScenarioUtils.createScenario(config);
		Population population = scenario.getPopulation();
		Network network = scenario.getNetwork();

		Set<String> linkModes = new HashSet<>();
		linkModes.add(mode);

		for (Link link : network.getLinks().values()) {
			link.setAllowedModes(linkModes);
		}

		PopulationFactory pf = population.getFactory();
		Plan plan = pf.createPlan();

		Person person = pf.createPerson(Id.createPersonId("1"));

		Leg leg = pf.createLeg(mode);

		plan.addLeg(leg);

		Leg leg2 = pf.createLeg(mode);
		plan.addLeg(leg2);

		person.addPlan(plan);
		person.setSelectedPlan(plan);

		population.addPerson(person);

		ScoringConfigGroup.ActivityParams params = new ScoringConfigGroup.ActivityParams("home");
		params.setScoringThisActivityAtAll(false);
		scenario.getConfig().scoring().addActivityParams(params);

		ScoringConfigGroup.ActivityParams params2 = new ScoringConfigGroup.ActivityParams("work");
		params2.setScoringThisActivityAtAll(false);
		scenario.getConfig().scoring().addActivityParams(params2);

		ScoringConfigGroup.ActivityParams params3 = new ScoringConfigGroup.ActivityParams(mode + " interaction");
		params3.setScoringThisActivityAtAll(false);
		scenario.getConfig().scoring().addActivityParams(params3);
		var rideParams = scenario.getConfig().routing().getTeleportedModeParams().get(SBBModes.RIDE);
		scenario.getConfig().routing().removeParameterSet(rideParams);

		//config.routing().setNetworkModes(List.of(SBBModes.CAR,SBBModes.RIDE));
		SBBNetworkRoutingModule.prepareScenario(scenario);
		ZonesModule.addZonestoScenario(scenario);
		AccessEgressModule.prepareLinkAttributes(scenario, false);

		Leg l1 = pf.createLeg(SBBModes.PT);
		Leg l2 = pf.createLeg(SBBModes.ACCESS_EGRESS_WALK);
		Leg l3 = pf.createLeg(SBBModes.CAR);
		Leg l4 = pf.createLeg(SBBModes.RIDEFEEDER);
		Leg l5 = pf.createLeg(SBBModes.WALK_MAIN_MAINMODE);
		Activity a = pf.createActivityFromCoord("test", new Coord(0, 0));
		MainModeIdentifier mainModeIdentifier = new SBBAnalysisMainModeIdentifier();

		List<PlanElement> ptTrip = List.of(l1, a, l2);


	}
}