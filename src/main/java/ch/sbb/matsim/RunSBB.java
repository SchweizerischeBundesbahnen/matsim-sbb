/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim;


import ch.sbb.matsim.config.*;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import ch.sbb.matsim.analysis.SBBPostProcessingOutputHandler;
import ch.sbb.matsim.preparation.PopulationSampler.SBBPopulationSampler;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ScoringFunctionFactory;

import ch.sbb.matsim.mobsim.qsim.SBBQSimModule;
import ch.sbb.matsim.routing.access.AccessEgress;
import ch.sbb.matsim.scoring.SBBScoringFunctionFactory;

/**
 * @author denism
 *
 */
public class RunSBB {

    private final static Logger log = Logger.getLogger(RunSBB.class);

    public static void main(String[] args) {
        System.setProperty("matsim.preferLocalDtds", "true");

        final String configFile = args[0];

        log.info(configFile);

        final Config config = buildConfig(configFile);

        Scenario scenario = ScenarioUtils.loadScenario(config);

        Controler controler = new Controler(scenario);

        SBBPopulationSamplerConfigGroup samplerConfig = ConfigUtils.addOrGetModule(scenario.getConfig(), SBBPopulationSamplerConfigGroup.class);
        if(samplerConfig.getDoSample()){
            SBBPopulationSampler sbbPopulationSampler = new SBBPopulationSampler();
            sbbPopulationSampler.sample(scenario.getPopulation(), samplerConfig.getFraction());
        }

        ScoringFunctionFactory scoringFunctionFactory = new SBBScoringFunctionFactory(scenario);
        controler.setScoringFunctionFactory(scoringFunctionFactory);

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                this.addControlerListenerBinding().to(SBBPostProcessingOutputHandler.class);
            }
        });

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                addTravelTimeBinding("ride").to(networkTravelTime());
                addTravelDisutilityFactoryBinding("ride").to(carTravelDisutilityFactoryKey());

                addTravelTimeBinding("privateSFF").to(networkTravelTime());
                addTravelDisutilityFactoryBinding("privateSFF").to(carTravelDisutilityFactoryKey());

                addTravelTimeBinding("taxiSFF").to(networkTravelTime());
                addTravelDisutilityFactoryBinding("taxiSFF").to(carTravelDisutilityFactoryKey());

                install(new SBBQSimModule());
                install(new SwissRailRaptorModule());
            }
        });

        new AccessEgress(controler).installAccessTime();

        controler.run();
    }

    public static Config buildConfig(String filepath) {
        return ConfigUtils.loadConfig(filepath, new PostProcessingConfigGroup(), new SBBTransitConfigGroup(),
                new SBBBehaviorGroupsConfigGroup(),new SBBPopulationSamplerConfigGroup(), new SwissRailRaptorConfigGroup());
    }
}
