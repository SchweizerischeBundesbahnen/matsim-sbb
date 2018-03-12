/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2018.
 */

package ch.sbb.matsim.analysis;

import ch.sbb.matsim.config.PostProcessingConfigGroup;
import javafx.geometry.Pos;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.events.algorithms.EventWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.IOException;
import java.util.List;

public class RunSBBPostProcessing {
    private final static Logger log = Logger.getLogger(RunSBBPostProcessing.class);

    public static void main(String[] args) throws IOException {
        final String configFile = args[0];
        final String eventsFileName = args[1];
        final String outputPath = args[2];
        log.info(configFile);

        final Config config = ConfigUtils.loadConfig(configFile, new PostProcessingConfigGroup());
        PostProcessingConfigGroup ppConfig = ConfigUtils.addOrGetModule(config, PostProcessingConfigGroup.class);

        Scenario scenario = ScenarioUtils.loadScenario(config);
        EventsManager eventsManager = new EventsManagerImpl();

        List<EventWriter> eventWriters = SBBPostProcessingOutputHandler.buildEventWriters(scenario, ppConfig, outputPath);

        for (EventWriter eventWriter : eventWriters) {
            eventsManager.addHandler(eventWriter);
        }

        new MatsimEventsReader(eventsManager).readFile(eventsFileName);

        for (EventWriter eventWriter : eventWriters) {
            eventWriter.closeFile();
        }

        if (ppConfig.getWriteAgentsCSV() || ppConfig.getWritePlanElementsCSV()) {
            new PopulationToCSV(scenario).write(outputPath);
        }

        if (ppConfig.getVisumNetFile()) {
            new NetworkToVisumNetFile(scenario, ppConfig).write(outputPath);
        }
    }
}
