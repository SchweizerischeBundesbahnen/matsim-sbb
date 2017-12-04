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
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.events.algorithms.EventWriter;
import org.matsim.core.events.algorithms.EventWriterXML;
import org.matsim.core.events.handler.EventHandler;

import java.util.LinkedList;
import java.util.List;

public class SBBPostProcessingEventsHandling implements BeforeMobsimListener, IterationEndsListener {
    private final Scenario scenario;
    private final EventsManager eventsManager;
    private List<EventHandler> eventHandlers = new LinkedList<>();
    private int writeEventsInterval;
    private PostProcessingConfigGroup ppConfig;

    @Inject
    public SBBPostProcessingEventsHandling(
            final EventsManager eventsManager,
            final Scenario scenario,
            final ControlerConfigGroup config,
            final PostProcessingConfigGroup ppConfig) {
        this.eventsManager = eventsManager;
        this.scenario = scenario;
        this.writeEventsInterval = config.getWriteEventsInterval();
        this.configureEventHandlers(ppConfig);
    }

    private void configureEventHandlers(PostProcessingConfigGroup ppConfig) {
        if (ppConfig.getPtVolumes()){
            PtVolumeToCSV ptVolumeHandler = new PtVolumeToCSV();
            this.eventHandlers.add(ptVolumeHandler);
        }

        if (ppConfig.getTravelDiaries()){
            EventsToTravelDiaries diariesHandler = new EventsToTravelDiaries(this.scenario);
            this.eventHandlers.add(diariesHandler);
        }

        if (ppConfig.getEventsPerPerson()){
            EventsToEventsPerPersonTable eventsPerPersonHandler = new EventsToEventsPerPersonTable(this.scenario);
            this.eventHandlers.add(eventsPerPersonHandler);
        }

        if (ppConfig.getLinkVolumes()) {
            LinkVolumeToCSV linkVolumeHandler = new LinkVolumeToCSV(this.scenario);
            this.eventHandlers.add(linkVolumeHandler);
        }
    }

    @Override
    public void notifyBeforeMobsim(BeforeMobsimEvent event) {
        if ((this.writeEventsInterval > 0) && (event.getIteration() % writeEventsInterval == 0)) {
            for (EventHandler eventHandler : this.eventHandlers) {
                eventsManager.addHandler(eventHandler);
            }
        }
    }

    @Override
    public void notifyIterationEnds(IterationEndsEvent event) {
        for (EventHandler eventHandler : this.eventHandlers) {
            this.eventsManager.removeHandler(eventHandler);
        }
    }
}
