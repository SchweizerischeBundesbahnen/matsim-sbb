package ch.sbb.matsim.utils;

import ch.sbb.matsim.config.variables.SBBModes;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;

public class ReplaceWalkByWalkMain {

	public static void main(String[] args) {
		String inputFile = "\\\\k13536\\mobi\\50_Ergebnisse\\MOBi_2.2\\sim\\2.2.3_100pct\\prepared\\populationMerged\\sliced_10\\plans_0.xml.gz";
		String outPutFile = "\\\\k13536\\mobi\\40_Projekte\\20200330_MOBi_3.0\\sim\\2.7.x\\2.7.10_test_motograd_v03_withHermesAndMATSim12\\plans_0_walkmain.xml.gz";
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		StreamingPopulationWriter spw = new StreamingPopulationWriter();
		spw.startStreaming(outPutFile);
		StreamingPopulationReader spr = new StreamingPopulationReader(scenario);
		spr.addAlgorithm(
				person -> TripStructureUtils.getLegs(person.getSelectedPlan().getPlanElements())
						.stream()
						.filter(leg -> leg.getMode().equals(SBBModes.WALK_FOR_ANALYSIS))
						.forEach(leg -> leg.setMode(SBBModes.WALK_MAIN_MAINMODE)));

		spr.addAlgorithm(spw);
		spr.readFile(inputFile);
		spw.closeStreaming();
	}

}
