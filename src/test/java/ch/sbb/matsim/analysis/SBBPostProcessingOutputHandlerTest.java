package ch.sbb.matsim.analysis;


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
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.controler.events.BeforeMobsimEvent;
import org.matsim.core.events.handler.EventHandler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.testcases.MatsimTestUtils;

import java.util.ArrayList;
import java.util.List;

public class SBBPostProcessingOutputHandlerTest {

    @Rule
    public MatsimTestUtils utils = new MatsimTestUtils();
    /*
    * all CSV writers are set to false, so we expect no eventHendlers to be configured
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
    * some CSV writers are set, but it is the wrong iteration, so we expect no eventHendlers to be configured
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
    * some CSV writers are set and it is the right iteration, so we expect 5 eventHendlers to be configured:
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
    * some CSV writers are set and it is the last iteration, so we expect 5 eventHendlers to be configured:
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
    * some CSV writers are set, it is the last iteration AND outputs are dumped, so we expect 10 eventHendlers to be configured:
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
        OutputDirectoryHierarchy outputDirectoryHierarchy = new OutputDirectoryHierarchy(outputPath, OutputDirectoryHierarchy.OverwriteFileSetting.overwriteExistingFiles);

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
