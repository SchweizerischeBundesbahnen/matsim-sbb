/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim;


import ch.sbb.matsim.analysis.SBBPostProcessingEventsHandling;
import ch.sbb.matsim.scoring.SBBScoringFunctionFactory;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ScoringFunctionFactory;
import ch.sbb.matsim.analysis.SBBPostProcessing;
import ch.sbb.matsim.config.PostProcessingConfigGroup;

/**
 * @author denism
 *
 */
public class RunSBB {

    public static void main(String[] args) {

        Logger log = Logger.getLogger(RunSBB.class);
        final String configFile = args[0];

        log.info(configFile);

        final Config config = ConfigUtils.loadConfig(configFile, new PostProcessingConfigGroup());

        Scenario scenario = ScenarioUtils.loadScenario(config);

        Controler controler = new Controler(scenario);

        ScoringFunctionFactory scoringFunctionFactory = new SBBScoringFunctionFactory(scenario);
        controler.setScoringFunctionFactory(scoringFunctionFactory);

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                this.addControlerListenerBinding().to(SBBPostProcessingEventsHandling.class);
            }
        });

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                addTravelTimeBinding("ride").to(networkTravelTime());
                addTravelDisutilityFactoryBinding("ride").to(carTravelDisutilityFactoryKey());
            }
        });
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                addTravelTimeBinding("privateSFF").to(networkTravelTime());
                addTravelDisutilityFactoryBinding("privateSFF").to(carTravelDisutilityFactoryKey());
            }
        });
         controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                addTravelTimeBinding("taxiSFF").to(networkTravelTime());
                addTravelDisutilityFactoryBinding("taxiSFF").to(carTravelDisutilityFactoryKey());
            }
        });

        controler.run();
//        postProcessing.write();
    }
}
