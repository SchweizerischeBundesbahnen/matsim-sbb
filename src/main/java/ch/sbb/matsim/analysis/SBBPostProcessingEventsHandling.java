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
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.controler.listener.ShutdownListener;
import org.matsim.core.events.algorithms.EventWriter;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class SBBPostProcessingEventsHandling implements BeforeMobsimListener, IterationEndsListener, ShutdownListener {
    private final Scenario scenario;
    private OutputDirectoryHierarchy controlerIO ;
    private final EventsManager eventsManager;
    private List<EventWriter> eventWriters = new LinkedList<>();
    private int writeEventsInterval;
    private PostProcessingConfigGroup ppConfig;

    @Inject
    public SBBPostProcessingEventsHandling(
            final EventsManager eventsManager,
            final Scenario scenario,
            final OutputDirectoryHierarchy controlerIO,
            final ControlerConfigGroup config,
            final PostProcessingConfigGroup ppConfig) {
        this.eventsManager = eventsManager;
        this.scenario = scenario;
        this.controlerIO = controlerIO;
        this.writeEventsInterval = config.getWriteEventsInterval();
        this.ppConfig = ppConfig;
    }

    @Override
    public void notifyBeforeMobsim(BeforeMobsimEvent event) {
        if ((this.writeEventsInterval > 0) && (event.getIteration() % writeEventsInterval == 0)) {
            this.eventWriters = this.buildEventWriters(event);

            for (EventWriter eventWriter : this.eventWriters) {
                eventsManager.addHandler(eventWriter);
            }
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

    @Override
    public void notifyShutdown(ShutdownEvent event) {
        if (this.ppConfig.getWritePlansCSV()){
            new PopulationToCSV(this.scenario).write(
                    this.controlerIO.getOutputFilename("agents.csv"),
                    this.controlerIO.getOutputFilename("plan_elements.csv")
            );
        }
    }

    private List<EventWriter> buildEventWriters(BeforeMobsimEvent event) {
        List<EventWriter> eventWriters = new LinkedList<>();

        if (this.ppConfig.getPtVolumes()){
            PtVolumeToCSV ptVolumeWriter = new PtVolumeToCSV(
                    this.controlerIO.getIterationFilename(event.getIteration(),""));
            eventWriters.add(ptVolumeWriter);
        }

        if (this.ppConfig.getTravelDiaries()){
            EventsToTravelDiaries diariesWriter = new EventsToTravelDiaries(
                    this.scenario, this.controlerIO.getIterationFilename(event.getIteration(), ""));
            eventWriters.add(diariesWriter);
        }

        if (this.ppConfig.getEventsPerPerson()){
            EventsToEventsPerPersonTable eventsPerPersonWriter = new EventsToEventsPerPersonTable(
                    this.scenario, this.controlerIO.getIterationFilename(event.getIteration(), ""));
            eventWriters.add(eventsPerPersonWriter);
        }

        if (this.ppConfig.getLinkVolumes()) {
            LinkVolumeToCSV linkVolumeWriter = new LinkVolumeToCSV(
                    this.scenario, this.controlerIO.getIterationFilename(event.getIteration(), ""));
            eventWriters.add(linkVolumeWriter);
        }

        if (this.ppConfig.getVisumNetFile()) {
            NetworkToVisumNetFile networkToVisumNetFileWriter = new NetworkToVisumNetFile(
                    this.scenario, this.controlerIO.getIterationFilename(event.getIteration(), ""), ppConfig);
            eventWriters.add(networkToVisumNetFileWriter);
        }

        return eventWriters;
    }
}
