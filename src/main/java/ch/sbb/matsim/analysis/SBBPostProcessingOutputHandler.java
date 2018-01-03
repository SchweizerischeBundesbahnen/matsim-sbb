/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis;

import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.utils.EventsToEventsPerPersonTable;
import com.google.inject.Inject;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.events.algorithms.EventWriter;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class SBBPostProcessingOutputHandler implements BeforeMobsimListener, IterationEndsListener {
    private final Scenario scenario;
    private OutputDirectoryHierarchy controlerIO ;
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
    public void notifyBeforeMobsim(BeforeMobsimEvent event) {
        if ((this.ppConfig.getWriteOutputsInterval() > 0) && (event.getIteration() % this.ppConfig.getWriteOutputsInterval() == 0)) {
            this.eventWriters = this.buildEventWriters(this.controlerIO.getIterationFilename(event.getIteration(), ""), false);
        }

        if (event.getIteration() == this.config.getLastIteration()) {
            List<EventWriter> finalEventWriters = this.buildEventWriters(this.controlerIO.getOutputFilename(""), true);

            for (EventWriter eventWriter : finalEventWriters) {
                this.eventWriters.add(eventWriter);
            }
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
    }

    private List<EventWriter> buildEventWriters(final String filename, final boolean includeFinalOutputs) {
        List<EventWriter> eventWriters = new LinkedList<>();

        if (this.ppConfig.getPtVolumes()){
            PtVolumeToCSV ptVolumeWriter = new PtVolumeToCSV(filename);
            eventWriters.add(ptVolumeWriter);
        }

        if (this.ppConfig.getTravelDiaries()){
            EventsToTravelDiaries diariesWriter = new EventsToTravelDiaries(this.scenario, filename);
            eventWriters.add(diariesWriter);
        }

        if (this.ppConfig.getEventsPerPerson()){
            EventsToEventsPerPersonTable eventsPerPersonWriter = new EventsToEventsPerPersonTable(this.scenario, filename);
            eventWriters.add(eventsPerPersonWriter);
        }

        if (this.ppConfig.getLinkVolumes()) {
            LinkVolumeToCSV linkVolumeWriter = new LinkVolumeToCSV(this.scenario, filename);
            eventWriters.add(linkVolumeWriter);
        }

        if (includeFinalOutputs) {
            if (this.ppConfig.getVisumNetFile()) {
                NetworkToVisumNetFile networkToVisumNetFileWriter = new NetworkToVisumNetFile(this.scenario, filename, ppConfig);
                eventWriters.add(networkToVisumNetFileWriter);
            }

            PopulationToCSV populationWriter = new PopulationToCSV(this.scenario, filename);
            eventWriters.add(populationWriter);
        }

        return eventWriters;
    }
}
