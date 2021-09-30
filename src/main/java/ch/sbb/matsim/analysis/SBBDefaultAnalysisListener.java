/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis;

import ch.sbb.matsim.analysis.tripsandlegsanalysis.PtLinkVolumeAnalyzer;
import ch.sbb.matsim.analysis.tripsandlegsanalysis.PutSurveyWriter;
import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailDemandMatrixAggregator;
import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailDemandReporting;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.utils.ScenarioConsistencyChecker;
import ch.sbb.matsim.zones.ZonesCollection;
import com.google.inject.Inject;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.controler.listener.StartupListener;

public class SBBDefaultAnalysisListener implements IterationEndsListener, StartupListener {

    private final Scenario scenario;
    private final EventsManager eventsManager;
    private OutputDirectoryHierarchy controlerIO;
    private ControlerConfigGroup config;
    private PostProcessingConfigGroup ppConfig;
    private ZonesCollection zones;

    @Inject
    private RailDemandMatrixAggregator railDemandMatrixAggregator;

    @Inject
    private RailDemandReporting railDemandReporting;

    @Inject
    private PutSurveyWriter putSurveyWriter;

    @Inject
    private PtLinkVolumeAnalyzer ptLinkVolumeAnalyzer;

    @Inject
    public SBBDefaultAnalysisListener(
            final EventsManager eventsManager,
            final Scenario scenario,
            final OutputDirectoryHierarchy controlerIO,
            final ControlerConfigGroup config,
            final PostProcessingConfigGroup ppConfig,
            final ZonesCollection zones
    ) {
        this.eventsManager = eventsManager;
        this.scenario = scenario;
        this.controlerIO = controlerIO;
        this.config = config;
        this.ppConfig = ppConfig;
        this.zones = zones;

    }

    @Override
    public void notifyStartup(StartupEvent event) {
        ScenarioConsistencyChecker.writeLog(controlerIO.getOutputFilename("scenarioCheck.log"));
    }

    @Override
    public void notifyIterationEnds(IterationEndsEvent event) {
        double scalefactor = 1.0 / ppConfig.getSimulationSampleSize();

        String railTripsFilename = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("railDemandReport.csv")
                : controlerIO.getIterationFilename(event.getIteration(), "railDemandReport.csv");
        String putSurveyNew = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("putSurvey.csv")
                : controlerIO.getIterationFilename(event.getIteration(), "putSurvey.csv");
        if (railDemandReporting != null) {
            railDemandReporting.calcAndwriteIterationDistanceReporting(railTripsFilename, scalefactor);
        }
        int interval = this.ppConfig.getWriteOutputsInterval();
        if (ppConfig.isWriteRailMatrix()) {
            if (((interval > 0) && (event.getIteration() % interval == 0)) || event.getIteration() == this.config.getLastIteration()) {
                String railDemandAggregateFilename = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("railDemandAggregate.csv")
                        : controlerIO.getIterationFilename(event.getIteration(), "railDemandAggregate.csv");
                railDemandMatrixAggregator.aggregateAndWriteMatrix(scalefactor, railDemandAggregateFilename);
                String ptLinkUsageFilename = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("ptlinkvolumes.csv")
                        : controlerIO.getIterationFilename(event.getIteration(), "ptlinkvolumes.csv");
                ptLinkVolumeAnalyzer.writePtLinkUsage(ptLinkUsageFilename, scenario.getConfig().controler().getRunId(), scalefactor);
                putSurveyWriter.collectAndWritePUTSurvey(putSurveyNew);

            }
        }

    }

}
