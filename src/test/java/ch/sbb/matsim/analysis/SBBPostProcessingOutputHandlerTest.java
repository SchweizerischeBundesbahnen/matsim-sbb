package ch.sbb.matsim.analysis;


import ch.sbb.matsim.analysis.TestFixtures.LinkTestFixture;
import ch.sbb.matsim.analysis.TestFixtures.PtTestFixture;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
        PostProcessingConfigGroup ppConfig = this.getPostProcessingConfigGroup(10, false, false, false, false, false, false);
        config.addModule(ppConfig);

        Scenario scenario = ScenarioUtils.createScenario(config);
        Controler controler = new Controler(scenario);
        OutputDirectoryHierarchy controlerIO = this.getOutputDirectoryHierarchy();
        ControlerConfigGroup configGroup = new ControlerConfigGroup();

        int iteration = 10;

        SBBPostProcessingOutputHandler outputHandler = new SBBPostProcessingOutputHandler(
                eventsManager,
                scenario,
                controlerIO,
                configGroup,
                ppConfig,
                null
        );

        BeforeMobsimEvent event = new BeforeMobsimEvent(controler, iteration);

        outputHandler.notifyBeforeMobsim(event);

        Assert.assertEquals(0, eventsManager.getEventHandlers().size());
    }

    /*
     * some CSV writers are set, but it is the wrong iteration, so we expect no eventHandlers to be configured
     * */
    @Test
    public void testNotifyBeforeMobsimAllListenersWrongIt() {
        EventsManagerStub eventsManager = new EventsManagerStub();
        Config config = ConfigUtils.createConfig();
        PostProcessingConfigGroup ppConfig = this.getPostProcessingConfigGroup(10, true, true, true, true, true, true);
        config.addModule(ppConfig);

        Scenario scenario = ScenarioUtils.createScenario(config);
        Controler controler = new Controler(scenario);
        OutputDirectoryHierarchy controlerIO = this.getOutputDirectoryHierarchy();
        ControlerConfigGroup configGroup = new ControlerConfigGroup();

        int iteration = 9;

        SBBPostProcessingOutputHandler outputHandler = new SBBPostProcessingOutputHandler(
                eventsManager,
                scenario,
                controlerIO,
                configGroup,
                ppConfig,
                null
        );

        BeforeMobsimEvent event = new BeforeMobsimEvent(controler, iteration);

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
        PostProcessingConfigGroup ppConfig = this.getPostProcessingConfigGroup(10, true, true, true, true, true, true);
        config.addModule(ppConfig);

        Scenario scenario = ScenarioUtils.createScenario(config);
        Controler controler = new Controler(scenario);
        OutputDirectoryHierarchy controlerIO = this.getOutputDirectoryHierarchy();
        ControlerConfigGroup configGroup = new ControlerConfigGroup();

        int iteration = 10;
        controlerIO.createIterationDirectory(iteration);

        SBBPostProcessingOutputHandler outputHandler = new SBBPostProcessingOutputHandler(
                eventsManager,
                scenario,
                controlerIO,
                configGroup,
                ppConfig,
                null
        );

        BeforeMobsimEvent event = new BeforeMobsimEvent(controler, iteration);

        outputHandler.notifyBeforeMobsim(event);

        Assert.assertEquals(5, eventsManager.getEventHandlers().size());
    }

    /*
     * some CSV writers are set and it is the last iteration, so we expect 5 eventHandlers to be configured:
     * PtVolumeToCSV, EventsToTravelDiaries, EventToEventsPerPersonTable, LinkVolumeToCSV, PopulationToCSV, NetworkToVisumNetFile
     * */
    @Test
    public void testNotifyBeforeMobsimLastIt() {
        EventsManagerStub eventsManager = new EventsManagerStub();
        Config config = ConfigUtils.createConfig();
        PostProcessingConfigGroup ppConfig = this.getPostProcessingConfigGroup(10, true, true, true, true, true, true);
        config.addModule(ppConfig);

        Scenario scenario = ScenarioUtils.createScenario(config);
        Controler controler = new Controler(scenario);
        OutputDirectoryHierarchy controlerIO = this.getOutputDirectoryHierarchy();
        ControlerConfigGroup configGroup = new ControlerConfigGroup();
        configGroup.setLastIteration(11);

        int iteration = 11;
        controlerIO.createIterationDirectory(iteration);

        SBBPostProcessingOutputHandler outputHandler = new SBBPostProcessingOutputHandler(
                eventsManager,
                scenario,
                controlerIO,
                configGroup,
                ppConfig,
                null
        );

        BeforeMobsimEvent event = new BeforeMobsimEvent(controler, iteration);

        outputHandler.notifyBeforeMobsim(event);

        Assert.assertEquals(5, eventsManager.getEventHandlers().size());
    }
    /*
     * some CSV writers are set, it is the last iteration AND outputs are dumped, so we expect 10 eventHandlers to be configured:
     * PtVolumeToCSV (2x), EventsToTravelDiaries (2x), EventToEventsPerPersonTable (2x), LinkVolumeToCSV (2x), PopulationToCSV, NetworkToVisumNetFile
     * */
    @Test
    public void testNotifyBeforeMobsimAllListenersLastIt() {
        EventsManagerStub eventsManager = new EventsManagerStub();
        Config config = ConfigUtils.createConfig();
        PostProcessingConfigGroup ppConfig = this.getPostProcessingConfigGroup(10, true, true, true, true, true, true);
        config.addModule(ppConfig);

        Scenario scenario = ScenarioUtils.createScenario(config);
        Controler controler = new Controler(scenario);
        OutputDirectoryHierarchy controlerIO = this.getOutputDirectoryHierarchy();
        ControlerConfigGroup configGroup = new ControlerConfigGroup();
        configGroup.setLastIteration(10);

        int iteration = 10;
        controlerIO.createIterationDirectory(10);

        SBBPostProcessingOutputHandler outputHandler = new SBBPostProcessingOutputHandler(
                eventsManager,
                scenario,
                controlerIO,
                configGroup,
                ppConfig,
                null
        );

        BeforeMobsimEvent event = new BeforeMobsimEvent(controler, iteration);

        outputHandler.notifyBeforeMobsim(event);

        Assert.assertEquals(5, eventsManager.getEventHandlers().size());
    }

    @Test
    public void test_persistentPtVolumesToCSV() throws IOException {
        PtTestFixture testFixture = new PtTestFixture();
        testFixture.addSingleTransitDemand();

        Config config = ConfigUtils.createConfig();
        PostProcessingConfigGroup ppConfig = new PostProcessingConfigGroup();
        ppConfig.setFinalDailyVolumes(true);
        ppConfig.setPtVolumes(true);
        ppConfig.setTravelDiaries(false);
        config.addModule(ppConfig);

        Scenario scenario = ScenarioUtils.createScenario(config);
        Controler controler = new Controler(scenario);
        String outputPath = this.utils.getOutputDirectory();
        OutputDirectoryHierarchy controlerIO = new OutputDirectoryHierarchy(outputPath, OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles, CompressionType.gzip);
        ControlerConfigGroup configGroup = new ControlerConfigGroup();
        int lastIteration = 2;
        configGroup.setLastIteration(lastIteration);
        ppConfig.setWriteOutputsInterval(1);

        SBBPostProcessingOutputHandler outputHandler = new SBBPostProcessingOutputHandler(
                testFixture.eventsManager,
                testFixture.scenario,
                controlerIO,
                configGroup,
                ppConfig,
                null
        );

        StartupEvent startupEvent = new StartupEvent(controler);
        outputHandler.notifyStartup(startupEvent);
        for (int i = 0; i <= lastIteration; i++) {
            System.out.println("### Iteration " + i + " ###");
            controlerIO.createIterationDirectory(i);
            BeforeMobsimEvent beforeMobsimEvent = new BeforeMobsimEvent(controler, i);
            IterationEndsEvent iterationEndsEvent = new IterationEndsEvent(controler, i);

            outputHandler.notifyBeforeMobsim(beforeMobsimEvent);
            testFixture.addEvents();
            testFixture.eventsManager.resetHandlers(i);
            outputHandler.notifyIterationEnds(iterationEndsEvent);
        }
        ShutdownEvent shutdownEvent = new ShutdownEvent(startupEvent.getServices(), false);
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

    @Test
    public void test_persistentVisumNetworkEventWriter() throws IOException {
        LinkTestFixture testFixture = new LinkTestFixture();
        testFixture.addDemand();

        Config config = ConfigUtils.createConfig();
        PostProcessingConfigGroup ppConfig = new PostProcessingConfigGroup();
        ppConfig.setVisumNetFile(true);
        ppConfig.setFinalDailyVolumes(true);
        ppConfig.setTravelDiaries(false);
        config.addModule(ppConfig);

        Scenario scenario = ScenarioUtils.createScenario(config);
        Controler controler = new Controler(scenario);
        String outputPath = this.utils.getOutputDirectory();
        OutputDirectoryHierarchy controlerIO = new OutputDirectoryHierarchy(outputPath, OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles, CompressionType.gzip);
        ControlerConfigGroup configGroup = new ControlerConfigGroup();
        int lastIteration = 2;
        configGroup.setLastIteration(lastIteration);
        ppConfig.setWriteOutputsInterval(1);

        SBBPostProcessingOutputHandler outputHandler = new SBBPostProcessingOutputHandler(
                testFixture.eventsManager,
                testFixture.scenario,
                controlerIO,
                configGroup,
                ppConfig,
                null
        );

        StartupEvent startupEvent = new StartupEvent(controler);
        outputHandler.notifyStartup(startupEvent);
        for (int i = 0; i <= lastIteration; i++) {
            System.out.println("### Iteration " + i + " ###");
            controlerIO.createIterationDirectory(i);
            BeforeMobsimEvent beforeMobsimEvent = new BeforeMobsimEvent(controler, i);
            IterationEndsEvent iterationEndsEvent = new IterationEndsEvent(controler, i);

            outputHandler.notifyBeforeMobsim(beforeMobsimEvent);
            testFixture.addEvents();
            testFixture.eventsManager.resetHandlers(i);
            outputHandler.notifyIterationEnds(iterationEndsEvent);
        }
        ShutdownEvent shutdownEvent = new ShutdownEvent(startupEvent.getServices(), false);
        outputHandler.notifyShutdown(shutdownEvent);

        String expectedLinksDaily = "it;2;3;4\n0;0;1;1\n1;0;1;1\n2;0;1;1\n";
        String expectedLinks = "LINK_ID_SIM;FROMNODENO;TONODENO;VOLUME_SIM\n3;3;4;1.0\n4;4;5;1.0\n";

        Assert.assertEquals(expectedLinks, readResult(this.utils.getOutputDirectory() + "visum_volumes.csv.gz"));
        Assert.assertEquals(expectedLinksDaily, readResult(this.utils.getOutputDirectory() + "visum_volumes_daily.csv.gz"));
        Assert.assertEquals(
                readResult(this.utils.getOutputDirectory() + "ITERS/it." + lastIteration + "/" + lastIteration + ".visum_volumes.csv.gz"),
                readResult(this.utils.getOutputDirectory() + "visum_volumes.csv.gz"));
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

    private PostProcessingConfigGroup getPostProcessingConfigGroup(int writeOutputsInterval, boolean eventPerPerson, boolean linkVolumes, boolean ptVolumes, boolean travelDiaries, boolean writePlansCSV, boolean visumNetFile) {
        PostProcessingConfigGroup ppConfig = new PostProcessingConfigGroup();
        ppConfig.setWriteOutputsInterval(writeOutputsInterval);
        ppConfig.setEventsPerPerson(eventPerPerson);
        ppConfig.setLinkVolumes(linkVolumes);
        ppConfig.setPtVolumes(ptVolumes);
        ppConfig.setTravelDiaries(travelDiaries);
        ppConfig.setMapActivitiesToZone(false);
        ppConfig.setWriteAgentsCSV(writePlansCSV);
        ppConfig.setWritePlanElementsCSV(false);
        ppConfig.setVisumNetFile(visumNetFile);

        return ppConfig;
    }

    private OutputDirectoryHierarchy getOutputDirectoryHierarchy() {
        String outputPath = this.utils.getOutputDirectory();
        OutputDirectoryHierarchy outputDirectoryHierarchy = new OutputDirectoryHierarchy(outputPath, OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles, CompressionType.gzip);

        return outputDirectoryHierarchy;
    }

    private static class EventsManagerStub implements EventsManager
    {
        List<EventHandler> eventHandlers = new ArrayList<EventHandler>();

        public List<EventHandler> getEventHandlers() {
            return this.eventHandlers;
        }

        public void processEvent(final Event event) {}

        public void addHandler(final EventHandler handler) {
            this.eventHandlers.add(handler);
        }

        public void removeHandler(final EventHandler handler) {
            this.eventHandlers.remove(handler);
        }

        public void resetHandlers(int iteration) {
            this.eventHandlers.clear();
        }

        public void initProcessing() {}

        public void afterSimStep(double time) {}

        public void finishProcessing() {}
    }

}
