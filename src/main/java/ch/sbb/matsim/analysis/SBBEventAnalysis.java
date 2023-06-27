/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis;

import ch.sbb.matsim.analysis.linkAnalysis.IterationLinkAnalyzer;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.zones.ZonesCollection;
import com.google.inject.Inject;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.BeforeMobsimListener;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.controler.listener.ShutdownListener;
import org.matsim.core.controler.listener.StartupListener;

import java.util.LinkedList;
import java.util.List;

public class SBBEventAnalysis implements BeforeMobsimListener, IterationEndsListener, StartupListener, ShutdownListener {

    private final Scenario scenario;
    private final EventsManager eventsManager;
    private final IterationLinkAnalyzer iterationLinkAnalyzer;
    private final OutputDirectoryHierarchy controlerIO;
    private List<EventsAnalysis> analyses = new LinkedList<>();
    private List<EventsAnalysis> persistentAnalyses = new LinkedList<>();
    private final ControlerConfigGroup config;
    private final PostProcessingConfigGroup ppConfig;
    private final ZonesCollection zones;

    @Inject
    public SBBEventAnalysis(
            final EventsManager eventsManager,
            final Scenario scenario,
            final OutputDirectoryHierarchy controlerIO,
            final ControlerConfigGroup config,
            final PostProcessingConfigGroup ppConfig,
            final ZonesCollection zones,
            IterationLinkAnalyzer iterationLinkAnalyzer
    ) {
        this.eventsManager = eventsManager;
        this.scenario = scenario;
        this.controlerIO = controlerIO;
        this.config = config;
        this.ppConfig = ppConfig;
        this.zones = zones;
        this.iterationLinkAnalyzer = iterationLinkAnalyzer;

    }

    static List<EventsAnalysis> buildEventWriters(final Scenario scenario, final PostProcessingConfigGroup ppConfig, final String filename, final ZonesCollection zones) {
        List<EventsAnalysis> analyses = new LinkedList<>();
        if (ppConfig.getLinkVolumes()) {
            LinkVolumeToCSV linkVolumeWriter = new LinkVolumeToCSV(scenario, filename);
            analyses.add(linkVolumeWriter);
        }
        return analyses;
    }


    @Override
    public void notifyStartup(StartupEvent event) {
        String outputDirectory = this.controlerIO.getOutputFilename("");
        if (this.ppConfig.getWriteAgentsCSV() || this.ppConfig.getWritePlanElementsCSV()) {
            new PopulationToCSV(scenario).write(outputDirectory);
        }

    }

    @Override
    public void notifyBeforeMobsim(BeforeMobsimEvent event) {
        int iteration = event.getIteration();
        int interval = this.ppConfig.getWriteOutputsInterval();

        if (ppConfig.getDailyLinkVolumes()) {
            eventsManager.addHandler(iterationLinkAnalyzer);
        }
        if (((interval > 0) && (iteration % interval == 0)) || iteration == this.config.getLastIteration()) {
            this.analyses = buildEventWriters(this.scenario, this.ppConfig, this.controlerIO.getIterationFilename(event.getIteration(), ""), this.zones);
            if (!ppConfig.getDailyLinkVolumes()) {
                eventsManager.addHandler(iterationLinkAnalyzer);
            }
        }
        for (EventsAnalysis analysis : this.analyses) {
            eventsManager.addHandler(analysis);
        }

    }

    @Override
    public void notifyIterationEnds(IterationEndsEvent event) {
        for (EventsAnalysis analysis : this.analyses) {
            analysis.writeResults(event.getIteration() == this.config.getLastIteration());
            this.eventsManager.removeHandler(analysis);
        }
        eventsManager.removeHandler(iterationLinkAnalyzer);
        this.analyses.clear();
    }

    @Override
    public void notifyShutdown(ShutdownEvent event) {
        for (EventsAnalysis analysis : this.persistentAnalyses) {
            analysis.writeResults(true);
        }
    }
}
