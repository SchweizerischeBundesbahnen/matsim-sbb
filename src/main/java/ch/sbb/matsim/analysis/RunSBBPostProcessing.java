/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesLoader;
import java.util.List;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.scenario.ScenarioUtils;

public class RunSBBPostProcessing {

	private final static Logger log = Logger.getLogger(RunSBBPostProcessing.class);

	public static void main(String[] args) {
		final String configFile = args[0];
		final String eventsFileName = args[1];
		final String outputPath = args[2];
		log.info(configFile);

        final Config config = ConfigUtils.loadConfig(configFile, RunSBB.sbbDefaultConfigGroups);
        PostProcessingConfigGroup ppConfig = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class);

        ZonesCollection allZones = new ZonesCollection();
        ZonesLoader.loadAllZones(config, allZones);

        Scenario scenario = ScenarioUtils.loadScenario(config);
        EventsManager eventsManager = new EventsManagerImpl();

        List<EventsAnalysis> eventWriters = SBBEventAnalysis.buildEventWriters(scenario, ppConfig, outputPath, allZones);

        for (EventsAnalysis eventWriter : eventWriters) {
            eventsManager.addHandler(eventWriter);
        }

        new MatsimEventsReader(eventsManager).readFile(eventsFileName);

        for (EventsAnalysis eventWriter : eventWriters) {
            eventWriter.writeResults(true);
        }

		if (ppConfig.getWriteAgentsCSV() || ppConfig.getWritePlanElementsCSV()) {
			new PopulationToCSV(scenario).write(outputPath);
		}

	}
}
