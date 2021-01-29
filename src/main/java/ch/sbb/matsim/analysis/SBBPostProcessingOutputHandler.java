/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis;

import ch.sbb.matsim.analysis.LinkAnalyser.ScreenLines.ScreenLineEventWriter;
import ch.sbb.matsim.analysis.LinkAnalyser.VisumNetwork.VisumNetworkEventWriter;
import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailDemandMatrixAggregator;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.utils.EventsToEventsPerPersonTable;
import ch.sbb.matsim.zones.ZonesCollection;
import com.google.inject.Inject;
import java.util.LinkedList;
import java.util.List;
import org.apache.log4j.Logger;
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

public class SBBPostProcessingOutputHandler implements BeforeMobsimListener, IterationEndsListener, StartupListener, ShutdownListener {

	private static final Logger log = Logger.getLogger(SBBPostProcessingOutputHandler.class);

	private final Scenario scenario;
	private final EventsManager eventsManager;
	private OutputDirectoryHierarchy controlerIO;
	private List<EventsAnalysis> analyses = new LinkedList<>();
	private List<EventsAnalysis> persistentAnalyses = new LinkedList<>();
	private ControlerConfigGroup config;
	private PostProcessingConfigGroup ppConfig;
	private ZonesCollection zones;

	@Inject
	private RailDemandMatrixAggregator railDemandMatrixAggregator;

	@Inject
	public SBBPostProcessingOutputHandler(
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

	static List<EventsAnalysis> buildEventWriters(final Scenario scenario, final PostProcessingConfigGroup ppConfig, final String filename, final ZonesCollection zones) {
		Double scaleFactor = 1.0 / scenario.getConfig().qsim().getFlowCapFactor();
		List<EventsAnalysis> analyses = new LinkedList<>();

		if (ppConfig.getPtVolumes()) {
			PtVolumeToCSV ptVolumeWriter = new PtVolumeToCSV(scenario, filename, false);
			analyses.add(ptVolumeWriter);
		}

		if (ppConfig.getTravelDiaries()) {
			EventsToTravelDiaries diariesWriter = new EventsToTravelDiaries(scenario, filename, zones);
			analyses.add(diariesWriter);
		}

		if (ppConfig.getEventsPerPerson()) {
			EventsToEventsPerPersonTable eventsPerPersonWriter = new EventsToEventsPerPersonTable(scenario, filename);
			analyses.add(eventsPerPersonWriter);
		}

		if (ppConfig.getLinkVolumes()) {
			LinkVolumeToCSV linkVolumeWriter = new LinkVolumeToCSV(scenario, filename);
			analyses.add(linkVolumeWriter);
		}

		if (ppConfig.getVisumNetFile()) {
			VisumNetworkEventWriter visumNetworkEventWriter = new VisumNetworkEventWriter(scenario, scaleFactor, ppConfig.getVisumNetworkMode(), filename, false);
			analyses.add(visumNetworkEventWriter);
		}

		if (ppConfig.getAnalyseScreenline()) {
			ScreenLineEventWriter screenLineEventWriter = new ScreenLineEventWriter(scenario, scaleFactor, ppConfig.getShapefileScreenline(), filename);
			analyses.add(screenLineEventWriter);
		}

		return analyses;
	}

	static List<EventsAnalysis> buildPersistentEventWriters(final Scenario scenario, final PostProcessingConfigGroup ppConfig, final String filename) {
		List<EventsAnalysis> persistentAnalyses = new LinkedList<>();

		PtVolumeToCSV ptVolumeWriter = new PtVolumeToCSV(scenario, filename, true);
		persistentAnalyses.add(ptVolumeWriter);

		VisumNetworkEventWriter visumNetworkEventWriter = new VisumNetworkEventWriter(scenario, 1.0,
				ppConfig.getVisumNetworkMode(), filename, true);
		persistentAnalyses.add(visumNetworkEventWriter);

		return persistentAnalyses;
	}

	@Override
	public void notifyStartup(StartupEvent event) {
		String outputDirectory = this.controlerIO.getOutputFilename("");

		if (this.ppConfig.getWriteAgentsCSV() || this.ppConfig.getWritePlanElementsCSV()) {
			new PopulationToCSV(scenario).write(outputDirectory);
		}
		if (this.ppConfig.getFinalDailyVolumes()) {
			this.persistentAnalyses = buildPersistentEventWriters(this.scenario, this.ppConfig, this.controlerIO.getOutputFilename(""));
			for (EventsAnalysis analysis : this.persistentAnalyses) {
				eventsManager.addHandler(analysis);
			}
		}
	}

	@Override
	public void notifyBeforeMobsim(BeforeMobsimEvent event) {
		int iteration = event.getIteration();
		int interval = this.ppConfig.getWriteOutputsInterval();

		if (((interval > 0) && (iteration % interval == 0)) || iteration == this.config.getLastIteration()) {
			this.analyses = buildEventWriters(this.scenario, this.ppConfig, this.controlerIO.getIterationFilename(event.getIteration(), ""), this.zones);
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
		int interval = this.ppConfig.getWriteOutputsInterval();
		if (ppConfig.isWriteRailMatrix()) {
			if (((interval > 0) && (event.getIteration() % interval == 0)) || event.getIteration() == this.config.getLastIteration()) {
				double scalefactor = 1.0 / scenario.getConfig().qsim().getFlowCapFactor();
				String filename = event.getIteration() == this.config.getLastIteration() ? controlerIO.getOutputFilename("railDemandAggregate.csv")
						: controlerIO.getIterationFilename(event.getIteration(), "railDemandAggregate.csv");
				railDemandMatrixAggregator.aggregateAndWriteMatrix(scalefactor, filename);
			}
		}

		this.analyses.clear();
	}

	@Override
	public void notifyShutdown(ShutdownEvent event) {
		for (EventsAnalysis analysis : this.persistentAnalyses) {
			analysis.writeResults(true);
		}
	}
}
