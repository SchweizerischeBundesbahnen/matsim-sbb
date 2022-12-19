/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis;

import ch.sbb.matsim.analysis.linkAnalysis.CarLinkAnalysis;
import ch.sbb.matsim.analysis.linkAnalysis.IterationLinkAnalyzer;
import ch.sbb.matsim.analysis.tripsandlegsanalysis.*;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.utils.ScenarioConsistencyChecker;
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
    private final OutputDirectoryHierarchy controlerIO;
    private final ControlerConfigGroup config;
    private final PostProcessingConfigGroup ppConfig;

    @Inject
    private RailDemandMatrixAggregator railDemandMatrixAggregator;

    @Inject
    private RailDemandReporting railDemandReporting;

    @Inject
    private PutSurveyWriter putSurveyWriter;

    @Inject
    private PtLinkVolumeAnalyzer ptLinkVolumeAnalyzer;

    @Inject
    private TripsAndDistanceStats tripsAndDistanceStats;
    @Inject
    private ActivityWriter activityWriter;

    private final CarLinkAnalysis carLinkAnalysis;

    @Inject
    public SBBDefaultAnalysisListener(
            final EventsManager eventsManager,
            final Scenario scenario,
            final OutputDirectoryHierarchy controlerIO,
            final ControlerConfigGroup config,
            final PostProcessingConfigGroup ppConfig,
            IterationLinkAnalyzer iterationLinkAnalyzer
    ) {
        this.scenario = scenario;
        this.controlerIO = controlerIO;
        this.config = config;
        this.ppConfig = ppConfig;
        this.carLinkAnalysis = new CarLinkAnalysis(ppConfig, scenario, iterationLinkAnalyzer);
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
        if (ppConfig.isWriteAnalsysis()) {
            if (((interval > 0) && (event.getIteration() % interval == 0)) || event.getIteration() == this.config.getLastIteration()) {
                String railDemandAggregateFilename = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("railDemandAggregate.csv")
                        : controlerIO.getIterationFilename(event.getIteration(), "railDemandAggregate.csv");
                railDemandMatrixAggregator.aggregateAndWriteMatrix(scalefactor, railDemandAggregateFilename);
                String ptLinkUsageFilename = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("ptlinkvolumes")
                        : controlerIO.getIterationFilename(event.getIteration(), "ptlinkvolumes");
                ptLinkVolumeAnalyzer.writePtLinkUsage(ptLinkUsageFilename, scenario.getConfig().controler().getRunId(), scalefactor);
                putSurveyWriter.collectAndWritePUTSurvey(putSurveyNew);
                String carVolumesName = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("car_volumes.att")
                        : controlerIO.getIterationFilename(event.getIteration(), "car_volumes.att");
                carLinkAnalysis.writeSingleIterationCarStats(carVolumesName);
                String tripsAndDistanceStatsName = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("trips_distance_stats.csv")
                        : controlerIO.getIterationFilename(event.getIteration(), "trips_distance_stats.csv");
                String fullTripsAndDistanceStatsName = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("trips_distance_stats_full.csv")
                        : controlerIO.getIterationFilename(event.getIteration(), "trips_distance_stats_full.csv");
                tripsAndDistanceStats.analyzeAndWriteStats(fullTripsAndDistanceStatsName, tripsAndDistanceStatsName);

                String activityFilename = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("matsim_activities.csv.gz")
                        : controlerIO.getIterationFilename(event.getIteration(), "matsim_activities.csv.gz");
                activityWriter.writeActivities(activityFilename);

            }
        }
        if (ppConfig.getDailyLinkVolumes()) {
            String carVolumesFile = controlerIO.getOutputFilename("car_volumes_daily.csv.gz");
            carLinkAnalysis.writeMultiIterationCarStats(carVolumesFile, event.getIteration());
        }
    }

}
