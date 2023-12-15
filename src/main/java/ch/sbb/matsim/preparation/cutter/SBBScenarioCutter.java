package ch.sbb.matsim.preparation.cutter;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.zones.ZonesLoader;
import org.apache.logging.log4j.LogManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigWriter;
import org.matsim.core.config.groups.ReplanningConfigGroup;
import org.matsim.core.config.groups.RoutingConfigGroup;
import org.matsim.core.config.groups.ScoringConfigGroup;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;

import java.io.IOException;

public class SBBScenarioCutter {

	/*
	 *
	 */
	public static void main(String[] args) throws IOException {

		String inputConfig;
		String newInputRelativeToNewConfig;
		String newConfig;
		String innerExtentShapeFile;
		String networkExtentShapeFile;
		String outerExtentShapeFile;
		String originalRunDirectory;

		String newRunId;
		String originalRunId;
		double newScenarioSampleSize;
		boolean parseEvents;
		boolean cutNetworkAndPlans;

		if (args.length == 12) {
			LogManager.getLogger(SBBScenarioCutter.class).info("Will use input files defined by args!");
			inputConfig = args[0];
			newConfig = args[1];
			newInputRelativeToNewConfig = args[2];

			innerExtentShapeFile = args[3];
			outerExtentShapeFile = args[4];
			networkExtentShapeFile = args[5];

			originalRunDirectory = args[6];
			originalRunId = args[7];

			newRunId = args[8];
			newScenarioSampleSize = Double.parseDouble(args[9]);
			parseEvents = Boolean.parseBoolean(args[10]);
			cutNetworkAndPlans = Boolean.parseBoolean(args[11]);

		} else {
			// define your cut from code, see below:
			LogManager.getLogger(SBBScenarioCutter.class).info("Will use input files defined in code!");
			inputConfig = "";
			newConfig = "";
			newInputRelativeToNewConfig = "input/";

			innerExtentShapeFile = "";
			networkExtentShapeFile = "";
			outerExtentShapeFile = "";

			originalRunDirectory = "";
			originalRunId = "";

			newRunId = "";
			newScenarioSampleSize = 1.0;
			parseEvents = false;
			cutNetworkAndPlans = false;
		}

		final String zonesIdAttribute = "ID";
		final String zonesId = "id";
		CutExtent inside = new ShapeExtent(ZonesLoader.loadZones
				(zonesId, innerExtentShapeFile, zonesIdAttribute));

		CutExtent outside = new ShapeExtent(ZonesLoader.loadZones
				(zonesId, outerExtentShapeFile, zonesIdAttribute));

		CutExtent network = new ShapeExtent(ZonesLoader.loadZones
				(zonesId, networkExtentShapeFile, zonesIdAttribute));

		Config config = RunSBB.buildConfig(inputConfig);

		String cutterOutputDirectory = ConfigGroup.getInputFileURL(config.getContext(), newInputRelativeToNewConfig.replace("/", "")).getFile();
		LogManager.getLogger(SBBScenarioCutter.class).info("Will write new scenario to " + cutterOutputDirectory);

		ScenarioCutter.run(originalRunDirectory, originalRunId, cutterOutputDirectory, newScenarioSampleSize, parseEvents, inside, outside, network, cutNetworkAndPlans);

		config.controller().setRunId(newRunId);
		adjustConfig(config, newInputRelativeToNewConfig);
		new ConfigWriter(config).write(newConfig);

	}

	public static void adjustConfig(Config config, String inbase) {

		config.facilities().setInputFile(inbase + "/facilities.xml.gz");

		config.network().setInputFile(inbase + "/network.xml.gz");
		config.network().setChangeEventsInputFile(inbase + "/networkChangeEvents.xml.gz");
		config.network().setTimeVariantNetwork(true);

		config.plans().setInputFile(inbase + "/population.xml.gz");

		config.transit().setTransitScheduleFile(inbase + "/schedule.xml.gz");
		config.transit().setVehiclesFile(inbase + "/transitVehicles.xml.gz");

		config.vehicles().setVehiclesFile(inbase + "/vehicles.xml.gz");

		config.counts().setInputFile(null);

		// fix cut plans (no innovation, scoring parameters do not matter)
		ScoringConfigGroup.ActivityParams outsideParams = new ScoringConfigGroup.ActivityParams(ScenarioCutter.OUTSIDE_ACT_TYPE);
		outsideParams.setTypicalDuration(3600);
		outsideParams.setScoringThisActivityAtAll(false);
		config.scoring().addActivityParams(outsideParams);

		ReplanningConfigGroup.StrategySettings outsideStrategy = new ReplanningConfigGroup.StrategySettings();
		outsideStrategy.setStrategyName(DefaultPlanStrategiesModule.DefaultSelector.KeepLastSelected);
		outsideStrategy.setWeight(1.0);
		outsideStrategy.setSubpopulation(ScenarioCutter.OUTSIDE_AGENT_SUBPOP);
		config.replanning().addStrategySettings(outsideStrategy);

		//these values are not required, but neccessary for the simulation to run
		ScoringConfigGroup.ModeParams outsideMode = new ScoringConfigGroup.ModeParams(ScenarioCutter.OUTSIDE_LEG_MODE);
		outsideMode.setMarginalUtilityOfTraveling(0);
		outsideMode.setConstant(0);
		outsideMode.setMarginalUtilityOfDistance(0);
		outsideMode.setMonetaryDistanceRate(0);
		config.scoring().addModeParams(outsideMode);

		RoutingConfigGroup.TeleportedModeParams outsideRoutingParams = new RoutingConfigGroup.TeleportedModeParams(ScenarioCutter.OUTSIDE_LEG_MODE);
		outsideRoutingParams.setBeelineDistanceFactor(1.3);
		outsideRoutingParams.setTeleportedModeSpeed(8.0);
		config.routing().addTeleportedModeParams(outsideRoutingParams);

	}

}
