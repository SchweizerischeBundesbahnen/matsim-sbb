/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis;

import ch.sbb.matsim.analysis.linkAnalysis.CarLinkAnalysis;
import ch.sbb.matsim.analysis.linkAnalysis.IterationLinkAnalyzer;
import ch.sbb.matsim.analysis.modalsplit.ModalSplitStats;
import ch.sbb.matsim.analysis.tripsandlegsanalysis.*;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.utils.ScenarioConsistencyChecker;
import ch.sbb.matsim.zones.ZoneGeoPackageWriter;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesModule;
import com.google.inject.Inject;
import org.apache.logging.log4j.LogManager;
import org.matsim.analysis.TripsAndLegsWriter;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.groups.ControllerConfigGroup;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.events.IterationStartsEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.controler.listener.IterationEndsListener;
import org.matsim.core.controler.listener.IterationStartsListener;
import org.matsim.core.controler.listener.StartupListener;

public class SBBDefaultAnalysisListener implements IterationEndsListener, StartupListener, IterationStartsListener {

    private final OutputDirectoryHierarchy controlerIO;
    private final ControllerConfigGroup config;
    private final PostProcessingConfigGroup ppConfig;
    private final IterationLinkAnalyzer iterationLinkAnalyser;

    @Inject
    private DemandAggregator demandAggregator;

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
    @Inject
    private ScreenlineAnalyzer screenlineAnalyzer;

    @Inject
    private ModalSplitStats modalSplitStats;

    @Inject
    private Scenario scenario;

    @Inject
    private TripsAndLegsWriter.CustomTripsWriterExtension sbbTripsExtension;

    @Inject
    private EventsManager eventsManager;

    private final CarLinkAnalysis carLinkAnalysis;


    @Inject
    public SBBDefaultAnalysisListener(
            final Scenario scenario,
            final OutputDirectoryHierarchy controlerIO,
            final ControllerConfigGroup config,
            final PostProcessingConfigGroup ppConfig,
            IterationLinkAnalyzer iterationLinkAnalyzer
    ) {
        this.controlerIO = controlerIO;
        this.config = config;
        this.ppConfig = ppConfig;
        this.carLinkAnalysis = new CarLinkAnalysis(ppConfig, scenario, iterationLinkAnalyzer);
        this.iterationLinkAnalyser = iterationLinkAnalyzer;
    }

    @Override
    public void notifyStartup(StartupEvent event) {
        ScenarioConsistencyChecker.writeLog(controlerIO.getOutputFilename("scenarioCheck.log"));
        String outputDirectory = this.controlerIO.getOutputFilename("");
        if (this.ppConfig.getWriteAgentsCSV()) {
            new PopulationToCSV(scenario).write(outputDirectory);
        }
        ZonesCollection zones = (ZonesCollection) this.scenario.getScenarioElement(ZonesModule.SBB_ZONES);
        if (zones != null) {
            try {
                String outputFile = outputDirectory + "mobi-zones.gpkg";
                LogManager.getLogger(getClass()).info("Writing zones to GeoPackage: " + outputFile);
                ZoneGeoPackageWriter.writeGeoPackage(zones.getZones(Id.create("zones", Zones.class)), outputFile);
            } catch (Exception e) {
                LogManager.getLogger(getClass()).error("Error writing zones to GeoPackage", e);
            }
        }
    }

    @Override
    public void notifyIterationEnds(IterationEndsEvent event) {
        double scalefactor = 1.0 / ppConfig.getSimulationSampleSize();
        int interval = this.ppConfig.getWriteOutputsInterval();
        if (ppConfig.isWriteAnalsysis()) {
            if (((interval > 0) && (event.getIteration() % interval == 0)) || event.getIteration() == this.config.getLastIteration()) {
                String putSurveyNew = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("putSurvey.csv")
                        : controlerIO.getIterationFilename(event.getIteration(), "putSurvey.csv");
                String raildemandReportFilename = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("SBB_railDemandReport.csv")
                        : controlerIO.getIterationFilename(event.getIteration(), "SBB_railDemandReport.csv");
                String lineDemandFilename = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("lineDemandReport.csv")
                        : controlerIO.getIterationFilename(event.getIteration(), "lineDemandReport.csv");
                String railDemandAggregateFilename = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("railDemandAggregate.csv")
                        : controlerIO.getIterationFilename(event.getIteration(), "railDemandAggregate.csv");
                String railDemandStationToStation = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("railDemandStationToStation.csv.gz")
                        : controlerIO.getIterationFilename(event.getIteration(), "railDemandStationToStation.csv.gz");
                String railDemandStationToStationFQ = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("railDemandStationToStationFQ.csv.gz")
                        : controlerIO.getIterationFilename(event.getIteration(), "railDemandStationToStationFQ.csv.gz");
                String tripsPerMunFile = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("tripsPerMun.csv.gz")
                        : controlerIO.getIterationFilename(event.getIteration(), "tripsPerMun.csv.gz");
                String tripsPerAmrFile = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("tripsPerAmr.csv")
                        : controlerIO.getIterationFilename(event.getIteration(), "tripsPerAmr.csv");
                String ptLinkUsageFilename = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("ptlinkvolumes")
                        : controlerIO.getIterationFilename(event.getIteration(), "ptlinkvolumes");
                String carVolumesName = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("car_volumes.att")
                        : controlerIO.getIterationFilename(event.getIteration(), "car_volumes.att");
                String tripsAndDistanceStatsName = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("trips_distance_stats.csv")
                        : controlerIO.getIterationFilename(event.getIteration(), "trips_distance_stats.csv");
                String fullTripsAndDistanceStatsName = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("trips_distance_stats_full.csv")
                        : controlerIO.getIterationFilename(event.getIteration(), "trips_distance_stats_full.csv");
                String activityFilename = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("matsim_activities.csv.gz")
                        : controlerIO.getIterationFilename(event.getIteration(), "matsim_activities.csv.gz");
                String modalSpliteFileName = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("")
                        : controlerIO.getIterationFilename(event.getIteration(), "");
                String screenlinesFilename = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("screenlines.csv")
                        : controlerIO.getIterationFilename(event.getIteration(), "screenlines.csv");

                railDemandReporting.calcAndwriteIterationDistanceReporting(raildemandReportFilename, lineDemandFilename,scalefactor);
                demandAggregator.aggregateAndWriteMatrix(scalefactor, railDemandAggregateFilename, railDemandStationToStation, railDemandStationToStationFQ, tripsPerMunFile, tripsPerAmrFile);
                var ptVolumes = ptLinkVolumeAnalyzer.writePtLinkUsage(ptLinkUsageFilename, scalefactor);
                putSurveyWriter.collectAndWritePUTSurvey(putSurveyNew);
                carLinkAnalysis.writeSingleIterationStreetStats(carVolumesName);
                tripsAndDistanceStats.analyzeAndWriteStats(fullTripsAndDistanceStatsName, tripsAndDistanceStatsName);
                activityWriter.writeActivities(activityFilename);
                modalSplitStats.analyzeAndWriteStats(modalSpliteFileName);
                screenlineAnalyzer.writeScreenlineResults(screenlinesFilename,scalefactor,iterationLinkAnalyser.getIterationCounts(), ptVolumes);
            }
        }
        if (ppConfig.getDailyLinkVolumes()) {
            String carVolumesFile = controlerIO.getOutputFilename("car_volumes_daily.csv.gz");
            carLinkAnalysis.writeMultiIterationCarStats(carVolumesFile, event.getIteration());
        }
        eventsManager.removeHandler(iterationLinkAnalyser);
    }

    @Override
    public void notifyIterationStarts(IterationStartsEvent event) {
        if (this.config.getWriteTripsInterval() > 0 && (event.getIteration() % this.config.getWriteTripsInterval() == 0)) {
            ((SBBTripsExtension) sbbTripsExtension).prepareAddtionalTripAttributes();
        }
        if (ppConfig.getDailyLinkVolumes() || (this.config.getWriteTripsInterval() > 0 && (event.getIteration() % this.config.getWriteTripsInterval() == 0))) {
            eventsManager.addHandler(iterationLinkAnalyser);
        }

    }
}
