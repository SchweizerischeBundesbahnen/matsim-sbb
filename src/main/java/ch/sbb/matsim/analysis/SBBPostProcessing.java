/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.controler.OutputDirectoryHierarchy;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.scenario.ScenarioUtils;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.utils.EventsToEventsPerPersonTable;

import java.io.IOException;

public class SBBPostProcessing {

    PtVolumeToCSV ptHandler = null;
    EventsToTravelDiaries diariesHandler = null;
    EventsToEventsPerPersonTable eventsPerPersonHandler = null;
    LinkVolumeToCSV linkVolumeHandler = null;
    NetworkToVisumNetFile networkToVisumNetFileHandler = null;
    Controler controler;
    PostProcessingConfigGroup ppConfig;

    public SBBPostProcessing(Controler controler){
        this.controler = controler;
        String output = this.controler.getConfig().controler().getOutputDirectory();

        Scenario scenario = controler.getScenario();
        ppConfig = (PostProcessingConfigGroup) scenario.getConfig().getModule(PostProcessingConfigGroup.GROUP_NAME);

        if (ppConfig.getPtVolumes()) {
            ptHandler = new PtVolumeToCSV(output + "/");
            controler.addOverridingModule(new AbstractModule() {
                @Override
                public void install() {
                    this.addEventHandlerBinding().toInstance(ptHandler);
                }
            });
        }


        if (ppConfig.getTravelDiaries()) {
            diariesHandler = new EventsToTravelDiaries(scenario, output + "/");
            controler.addOverridingModule(new AbstractModule() {
                @Override
                public void install() {
                    this.addEventHandlerBinding().toInstance(diariesHandler);
                }
            });
        }

        if (ppConfig.getEventsPerPerson()) {
            eventsPerPersonHandler = new EventsToEventsPerPersonTable(scenario);
            controler.addOverridingModule(new AbstractModule() {
                @Override
                public void install() {
                    this.addEventHandlerBinding().toInstance(eventsPerPersonHandler);
                }
            });
        }

        if(ppConfig.getLinkVolumes()) {
            linkVolumeHandler = new LinkVolumeToCSV(scenario, null);
            controler.addOverridingModule(new AbstractModule() {
                @Override
                public void install() {
                    this.addEventHandlerBinding().toInstance(linkVolumeHandler);
                }
            });
        }

    }

    public void write(){
        String output = controler.getConfig().controler().getOutputDirectory();
        if(ppConfig.getWritePlansCSV()){
            new PopulationToCSV(controler.getScenario()).write(output+"/agents.csv", output+"/plan_elements.csv");
        }

        if(diariesHandler != null) {
            try {
                diariesHandler.writeSimulationResultsToTabSeparated("");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if(ptHandler != null) {
            ptHandler.write();
        }
        if(eventsPerPersonHandler != null) {
            try {
                eventsPerPersonHandler.writeSimulationResultsToTabSeparated(output, "");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (linkVolumeHandler != null)  {
            linkVolumeHandler.write(output + "/");
        }

        if (ppConfig.getVisumNetFile()) {
            networkToVisumNetFileHandler = new NetworkToVisumNetFile(this.controler.getScenario(),output + "/", ppConfig);
            networkToVisumNetFileHandler.write(output + "/net.net");
        }
    }

    public static void main(String[] args) {

        Logger log = Logger.getLogger(SBBPostProcessing.class);

        final String configFile = args[0];
        final String eventsFileName = args[1];
        log.info(configFile);

        final Config config = ConfigUtils.loadConfig(configFile, new PostProcessingConfigGroup());
        Scenario scenario = ScenarioUtils.loadScenario(config);

        Controler controler = new Controler(scenario);
        SBBPostProcessing postProcessing = new SBBPostProcessing(controler);

        EventsManager events = new EventsManagerImpl();

        if(postProcessing.ppConfig.getPtVolumes()){
            events.addHandler(postProcessing.ptHandler);
        }

        if (postProcessing.ppConfig.getLinkVolumes()) {
            events.addHandler(postProcessing.linkVolumeHandler);
        }

        if(postProcessing.ppConfig.getTravelDiaries()){
            events.addHandler(postProcessing.diariesHandler);
        }

        new MatsimEventsReader(events).readFile(eventsFileName);

        postProcessing.write();
    }
}
