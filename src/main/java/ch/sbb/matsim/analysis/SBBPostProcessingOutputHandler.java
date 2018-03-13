/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis;

import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.utils.EventsToEventsPerPersonTable;
import com.google.inject.Inject;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.controler.listener.StartupListener;
import org.matsim.core.events.algorithms.EventWriter;

import java.util.LinkedList;
import java.util.List;

public class SBBPostProcessingOutputHandler implements BeforeMobsimListener, IterationEndsListener, StartupListener {

    private static final Logger log = Logger.getLogger(SBBPostProcessingOutputHandler.class);

    private final Scenario scenario;
    private OutputDirectoryHierarchy controlerIO;
    private final EventsManager eventsManager;
    private List<EventWriter> eventWriters = new LinkedList<>();
    private ControlerConfigGroup config;
    private PostProcessingConfigGroup ppConfig;

    @Inject
    public SBBPostProcessingOutputHandler(
            final EventsManager eventsManager,
            final Scenario scenario,
            final OutputDirectoryHierarchy controlerIO,
            final ControlerConfigGroup config,
            final PostProcessingConfigGroup ppConfig) {
        this.eventsManager = eventsManager;
        this.scenario = scenario;
        this.controlerIO = controlerIO;
        this.config = config;
        this.ppConfig = ppConfig;
    }

    @Override
    public void notifyStartup(StartupEvent event)   {
        String outputDirectory = this.controlerIO.getOutputFilename("");

        if (this.ppConfig.getWriteAgentsCSV() || this.ppConfig.getWritePlanElementsCSV())
            new PopulationToCSV(scenario).write(outputDirectory);
    }

    @Override
    public void notifyBeforeMobsim(BeforeMobsimEvent event) {
        if ((this.ppConfig.getWriteOutputsInterval() > 0) && (event.getIteration() % this.ppConfig.getWriteOutputsInterval() == 0)) {
            this.eventWriters = this.buildEventWriters(this.scenario, this.ppConfig, this.controlerIO.getIterationFilename(event.getIteration(), ""));
        }

        if (event.getIteration() == this.config.getLastIteration()) {
            List<EventWriter> finalEventWriters = this.buildEventWriters(this.scenario, this.ppConfig, this.controlerIO.getOutputFilename(""));
            this.eventWriters.addAll(finalEventWriters);
        }

        for (EventWriter eventWriter : this.eventWriters) {
            eventsManager.addHandler(eventWriter);
        }
    }

    @Override
    public void notifyIterationEnds(IterationEndsEvent event) {
        for (EventWriter eventWriter : this.eventWriters) {
            eventWriter.closeFile();
            this.eventsManager.removeHandler(eventWriter);
        }

        this.eventWriters.clear();

        if (event.getIteration() == this.config.getLastIteration()) {
            // write final outputs
            String outputDirectory = this.controlerIO.getOutputFilename("");

            if (this.ppConfig.getVisumNetFile()) {
                new NetworkToVisumNetFile(scenario, ppConfig).write(outputDirectory);
            }
        }
    }

    public static List<EventWriter> buildEventWriters(final Scenario scenario, final PostProcessingConfigGroup ppConfig, final String filename) {
        List<EventWriter> eventWriters = new LinkedList<>();

        if (ppConfig.getPtVolumes()){
            PtVolumeToCSV ptVolumeWriter = new PtVolumeToCSV(filename);
            eventWriters.add(ptVolumeWriter);
        }

        if (ppConfig.getTravelDiaries()){
            EventsToTravelDiaries diariesWriter = new EventsToTravelDiaries(scenario, filename);
            eventWriters.add(diariesWriter);
        }

        if (ppConfig.getEventsPerPerson()){
            EventsToEventsPerPersonTable eventsPerPersonWriter = new EventsToEventsPerPersonTable(scenario, filename);
            eventWriters.add(eventsPerPersonWriter);
        }

        if (ppConfig.getLinkVolumes()) {
            LinkVolumeToCSV linkVolumeWriter = new LinkVolumeToCSV(scenario, filename);
            eventWriters.add(linkVolumeWriter);
        }

        return eventWriters;
    }
}
