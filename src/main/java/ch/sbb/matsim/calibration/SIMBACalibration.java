/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.calibration;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.MatsimNetworkReader;
import org.matsim.core.scenario.ScenarioUtils;
import java.io.File;

public class SIMBACalibration {
    public static void main(String[] args) {

        String eventsFileName = null;
        Config config = null;
        String outputDirectory = null;
        String visumVolume = null;

        config = ConfigUtils.loadConfig(args[0]);
        outputDirectory = config.controler().getOutputDirectory();

        eventsFileName = args[1];
        visumVolume = args[2];

        File f = new File(outputDirectory);

        Logger log = Logger.getLogger(SIMBACalibration.class);

        Scenario scenario = ScenarioUtils.loadScenario(config);

        //new MatsimNetworkReader(scenario.getNetwork()).readFile(config.network().getInputFile());

        final PTObjective VehicleJourneyVolume = new PTObjective(scenario, visumVolume);

        Controler controler = new Controler(scenario);

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                addEventHandlerBinding().toInstance(VehicleJourneyVolume);
            }
        });

        controler.run();

        log.info(VehicleJourneyVolume.getScore());
        VehicleJourneyVolume.write(f+"/belastung.csv");

    }
}
