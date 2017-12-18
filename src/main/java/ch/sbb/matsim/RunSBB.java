/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim;


import ch.sbb.matsim.config.SBBBehaviorGroupsConfigGroup;
import ch.sbb.matsim.scoring.SBBScoringFunctionFactory;
import ch.sbb.matsim.config.SBBTransitConfigGroup;
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
import ch.sbb.matsim.mobsim.qsim.SBBQSimModule;

/**
 * @author denism
 *
 */
public class RunSBB {

    private static Logger log = Logger.getLogger(RunSBB.class);

    public static void main(String[] args) {
        System.setProperty("matsim.preferLocalDtds", "true");

        final String configFile = args[0];

        log.info(configFile);

        final Config config = ConfigUtils.loadConfig(configFile, new PostProcessingConfigGroup(), new SBBTransitConfigGroup(),
                new SBBBehaviorGroupsConfigGroup());

        Scenario scenario = ScenarioUtils.loadScenario(config);

        Controler controler = new Controler(scenario);

        ScoringFunctionFactory scoringFunctionFactory = new SBBScoringFunctionFactory(scenario);
        controler.setScoringFunctionFactory(scoringFunctionFactory);

        SBBPostProcessing postProcessing = new SBBPostProcessing(controler);

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
            }
        });

        controler.run();
        postProcessing.write();
    }
}
