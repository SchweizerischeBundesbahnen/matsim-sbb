package ch.sbb.matsim.analysis;

import ch.sbb.matsim.analysis.TestFixtures.PtTestFixture;
import ch.sbb.matsim.analysis.linkAnalysis.IterationLinkAnalyzer;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.Event;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.ControlerConfigGroup;
import org.matsim.core.config.groups.ControlerConfigGroup.CompressionType;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.controler.events.IterationEndsEvent;
import org.matsim.core.controler.events.ShutdownEvent;
import org.matsim.core.controler.events.StartupEvent;
import org.matsim.core.events.handler.EventHandler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.testcases.MatsimTestUtils;

public class SBBPostProcessingOutputHandlerTest {

	@Rule
	public MatsimTestUtils utils = new MatsimTestUtils();

	/*
	 * all CSV writers are set to false, so we expect no eventHandlers to be configured
	 * */
	@Test
	public void testNotifyBeforeMobsimNoListeners() {
        EventsManagerStub eventsManager = new EventsManagerStub();
        Config config = ConfigUtils.createConfig();
        PostProcessingConfigGroup ppConfig = this.getPostProcessingConfigGroup(false, false, false, false, false, false);
        ppConfig.setDailyLinkVolumes(false);
        ppConfig.setSimulationSampleSize(1.0);
        config.addModule(ppConfig);

        Scenario scenario = ScenarioUtils.createScenario(config);
        Controler controler = new Controler(scenario);
        OutputDirectoryHierarchy controlerIO = this.getOutputDirectoryHierarchy();
        ControlerConfigGroup configGroup = new ControlerConfigGroup();

        int iteration = 10;

		SBBEventAnalysis outputHandler = new SBBEventAnalysis(
				eventsManager,
				scenario,
				controlerIO,
				configGroup,
				ppConfig,
				null, new IterationLinkAnalyzer()
		);

		BeforeMobsimEvent event = new BeforeMobsimEvent(controler, iteration, false);

        outputHandler.notifyBeforeMobsim(event);

		Assert.assertEquals(1, eventsManager.getEventHandlers().size());
    }

	/*
	 * some CSV writers are set, but it is the wrong iteration, so we expect no eventHandlers to be configured
	 * */
	@Test
	public void testNotifyBeforeMobsimAllListenersWrongIt() {
        EventsManagerStub eventsManager = new EventsManagerStub();
        Config config = ConfigUtils.createConfig();
        PostProcessingConfigGroup ppConfig = this.getPostProcessingConfigGroup(true, true, true, true, true, true);
        ppConfig.setSimulationSampleSize(1.0);

        config.addModule(ppConfig);

        Scenario scenario = ScenarioUtils.createScenario(config);
        Controler controler = new Controler(scenario);
        OutputDirectoryHierarchy controlerIO = this.getOutputDirectoryHierarchy();
        ControlerConfigGroup configGroup = new ControlerConfigGroup();

        int iteration = 9;

		SBBEventAnalysis outputHandler = new SBBEventAnalysis(
				eventsManager,
				scenario,
				controlerIO,
				configGroup,
				ppConfig,
				null, new IterationLinkAnalyzer()
		);

        BeforeMobsimEvent event = new BeforeMobsimEvent(controler, iteration, false);

        outputHandler.notifyBeforeMobsim(event);

        Assert.assertEquals(0, eventsManager.getEventHandlers().size());
    }

	/*
	 * some CSV writers are set and it is the right iteration, so we expect 5 eventHandlers to be configured:
	 * PtVolumeToCSV, EventsToTravelDiaries, EventToEventsPerPersonTable, LinkVolumeToCSV, VisumNetwork
	 * */
	@Test
	public void testNotifyBeforeMobsimAllListeners() {
        EventsManagerStub eventsManager = new EventsManagerStub();
        Config config = ConfigUtils.createConfig();
        PostProcessingConfigGroup ppConfig = this.getPostProcessingConfigGroup(true, true, true, true, true, true);
        ppConfig.setSimulationSampleSize(1.0);
        config.addModule(ppConfig);

        Scenario scenario = ScenarioUtils.createScenario(config);
        Controler controler = new Controler(scenario);
        OutputDirectoryHierarchy controlerIO = this.getOutputDirectoryHierarchy();
        ControlerConfigGroup configGroup = new ControlerConfigGroup();

        int iteration = 10;
        controlerIO.createIterationDirectory(iteration);

		SBBEventAnalysis outputHandler = new SBBEventAnalysis(
				eventsManager,
				scenario,
				controlerIO,
				configGroup,
				ppConfig,
				null, new IterationLinkAnalyzer()
		);

        BeforeMobsimEvent event = new BeforeMobsimEvent(controler, iteration, false);

        outputHandler.notifyBeforeMobsim(event);

		Assert.assertEquals(3, eventsManager.getEventHandlers().size());
    }

	/*
	 * some CSV writers are set and it is the last iteration, so we expect 5 eventHandlers to be configured:
	 * PtVolumeToCSV, EventsToTravelDiaries, EventToEventsPerPersonTable, LinkVolumeToCSV, PopulationToCSV, NetworkToVisumNetFile
	 * */
	@Test
	public void testNotifyBeforeMobsimLastIt() {
        EventsManagerStub eventsManager = new EventsManagerStub();
        Config config = ConfigUtils.createConfig();
        PostProcessingConfigGroup ppConfig = this.getPostProcessingConfigGroup(true, true, true, true, true, true);
        ppConfig.setSimulationSampleSize(1.0);
        config.addModule(ppConfig);

        Scenario scenario = ScenarioUtils.createScenario(config);
        Controler controler = new Controler(scenario);
        OutputDirectoryHierarchy controlerIO = this.getOutputDirectoryHierarchy();
        ControlerConfigGroup configGroup = new ControlerConfigGroup();
        configGroup.setLastIteration(11);

        int iteration = 11;
		controlerIO.createIterationDirectory(iteration);

		SBBEventAnalysis outputHandler = new SBBEventAnalysis(
				eventsManager,
				scenario,
				controlerIO,
				configGroup,
				ppConfig,
				null, new IterationLinkAnalyzer()
		);

		BeforeMobsimEvent event = new BeforeMobsimEvent(controler, iteration, false);

		outputHandler.notifyBeforeMobsim(event);

		Assert.assertEquals(3, eventsManager.getEventHandlers().size());
	}

	/*
	 * some CSV writers are set, it is the last iteration AND outputs are dumped, so we expect 10 eventHandlers to be configured:
	 * PtVolumeToCSV (2x), EventsToTravelDiaries (2x), EventToEventsPerPersonTable (2x), LinkVolumeToCSV (2x), PopulationToCSV, NetworkToVisumNetFile
	 * */
	@Test
	public void testNotifyBeforeMobsimAllListenersLastIt() {
        EventsManagerStub eventsManager = new EventsManagerStub();
        Config config = ConfigUtils.createConfig();
        PostProcessingConfigGroup ppConfig = this.getPostProcessingConfigGroup(true, true, true, true, true, true);
        ppConfig.setSimulationSampleSize(1.0);
        config.addModule(ppConfig);

        Scenario scenario = ScenarioUtils.createScenario(config);
        Controler controler = new Controler(scenario);
        OutputDirectoryHierarchy controlerIO = this.getOutputDirectoryHierarchy();
        ControlerConfigGroup configGroup = new ControlerConfigGroup();
        configGroup.setLastIteration(10);

        int iteration = 10;
		controlerIO.createIterationDirectory(10);

		SBBEventAnalysis outputHandler = new SBBEventAnalysis(
				eventsManager,
				scenario,
				controlerIO,
				configGroup,
				ppConfig,
				null, new IterationLinkAnalyzer()
		);

		BeforeMobsimEvent event = new BeforeMobsimEvent(controler, iteration, false);

		outputHandler.notifyBeforeMobsim(event);

		Assert.assertEquals(3, eventsManager.getEventHandlers().size());
	}

	@Test
	public void test_persistentPtVolumesToCSV() throws IOException {
		PtTestFixture testFixture = new PtTestFixture();
		testFixture.addSingleTransitDemand();

		Config config = ConfigUtils.createConfig();
		PostProcessingConfigGroup ppConfig = new PostProcessingConfigGroup();
		ppConfig.setSimulationSampleSize(1.0);
		ppConfig.setSimulationSampleSize(1.0);
		ppConfig.setFinalDailyVolumes(true);
		ppConfig.setPtVolumes(true);
		ppConfig.setWriteAnalsysis(false);
		ppConfig.setDailyLinkVolumes(true);
		config.addModule(ppConfig);

		Scenario scenario = ScenarioUtils.createScenario(config);
		Controler controler = new Controler(scenario);
		String outputPath = this.utils.getOutputDirectory();
		OutputDirectoryHierarchy controlerIO = new OutputDirectoryHierarchy(outputPath, OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles, CompressionType.gzip);
		ControlerConfigGroup configGroup = new ControlerConfigGroup();
		int lastIteration = 2;
		configGroup.setLastIteration(lastIteration);
		ppConfig.setWriteOutputsInterval(1);

		SBBEventAnalysis outputHandler = new SBBEventAnalysis(
				testFixture.eventsManager,
				testFixture.scenario,
				controlerIO,
				configGroup,
				ppConfig,
				null, new IterationLinkAnalyzer()
		);

		StartupEvent startupEvent = new StartupEvent(controler);
		outputHandler.notifyStartup(startupEvent);
		for (int i = 0; i <= lastIteration; i++) {
            System.out.println("### Iteration " + i + " ###");
            controlerIO.createIterationDirectory(i);
            BeforeMobsimEvent beforeMobsimEvent = new BeforeMobsimEvent(controler, i, false);
            IterationEndsEvent iterationEndsEvent = new IterationEndsEvent(controler, i, false);

            outputHandler.notifyBeforeMobsim(beforeMobsimEvent);
            testFixture.addEvents();
            testFixture.eventsManager.resetHandlers(i);
            outputHandler.notifyIterationEnds(iterationEndsEvent);
        }
        ShutdownEvent shutdownEvent = new ShutdownEvent(startupEvent.getServices(), false, 0);
        outputHandler.notifyShutdown(shutdownEvent);

        Assert.assertEquals(PtVolumeToCSVTest.expectedStops, readResult(this.utils.getOutputDirectory() + "matsim_stops.csv.gz"));
        Assert.assertEquals(PtVolumeToCSVTest.expectedVehJourneys, readResult(this.utils.getOutputDirectory() + "matsim_vehjourneys.csv.gz"));
        Assert.assertEquals(PtVolumeToCSVTest.expectedStopsDaily, readResult(this.utils.getOutputDirectory() + "matsim_stops_daily.csv.gz"));
        Assert.assertEquals(
                readResult(this.utils.getOutputDirectory() + "ITERS/it." + lastIteration + "/" + lastIteration + ".matsim_stops.csv.gz"),
                readResult(this.utils.getOutputDirectory() + "matsim_stops.csv.gz"));
        Assert.assertEquals(
                readResult(this.utils.getOutputDirectory() + "ITERS/it." + lastIteration + "/" + lastIteration + ".matsim_vehjourneys.csv.gz"),
				readResult(this.utils.getOutputDirectory() + "matsim_vehjourneys.csv.gz"));
	}


	private String readResult(String filePath) throws IOException {
		BufferedReader br = IOUtils.getBufferedReader(filePath);
		StringBuilder sb = new StringBuilder();
		String line = br.readLine();

        while (line != null) {
            sb.append(line);
            sb.append("\n");
            line = br.readLine();
        }

        return sb.toString();
    }

    private PostProcessingConfigGroup getPostProcessingConfigGroup(boolean eventPerPerson, boolean linkVolumes, boolean ptVolumes, boolean travelDiaries,
            boolean writePlansCSV, boolean visumNetFile) {
        PostProcessingConfigGroup ppConfig = new PostProcessingConfigGroup();
        ppConfig.setWriteOutputsInterval(10);
        ppConfig.setLinkVolumes(linkVolumes);
        ppConfig.setPtVolumes(ptVolumes);
        ppConfig.setWriteAgentsCSV(writePlansCSV);
        ppConfig.setWritePlanElementsCSV(false);

        return ppConfig;
    }

	private OutputDirectoryHierarchy getOutputDirectoryHierarchy() {
		String outputPath = this.utils.getOutputDirectory();

        return new OutputDirectoryHierarchy(outputPath, OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles, CompressionType.gzip);
	}

    private static class EventsManagerStub implements EventsManager {

        final List<EventHandler> eventHandlers = new ArrayList<>();

        public List<EventHandler> getEventHandlers() {
            return this.eventHandlers;
        }

        @Override
        public void processEvent(final Event event) {
        }

        @Override
		public void addHandler(final EventHandler handler) {
			this.eventHandlers.add(handler);
		}

		@Override
		public void removeHandler(final EventHandler handler) {
			this.eventHandlers.remove(handler);
		}

		@Override
		public void resetHandlers(int iteration) {
			this.eventHandlers.clear();
		}

		@Override
		public void initProcessing() {
		}

		@Override
		public void afterSimStep(double time) {
		}

		@Override
		public void finishProcessing() {
		}
	}

}
