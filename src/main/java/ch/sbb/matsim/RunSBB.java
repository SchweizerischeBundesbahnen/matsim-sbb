/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim;


import ch.sbb.matsim.config.SBBBehaviorGroupsConfigGroup;
import ch.sbb.matsim.scoring.SBBScoringFunctionFactory;
import ch.sbb.matsim.config.SBBTransitConfigGroup;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ScoringFunctionFactory;

import ch.sbb.matsim.analysis.LocateAct;
import ch.sbb.matsim.analysis.SBBPostProcessing;
import ch.sbb.matsim.config.AccessTimeConfigGroup;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.config.SBBTransitConfigGroup;
import ch.sbb.matsim.mobsim.qsim.SBBQSimModule;
import ch.sbb.matsim.routing.network.SBBNetworkRouter;
import ch.sbb.matsim.routing.teleportation.SBBBeelineTeleportationRouting;
import ch.sbb.matsim.scoring.SBBScoringFunctionFactory;


/**
 * @author denism
 *
 */
public class RunSBB {

    private static Logger log = Logger.getLogger(RunSBB.class);
    private Controler controler;
    private SBBPostProcessing postProcessing;

    public static void main(String[] args) {
        System.setProperty("matsim.preferLocalDtds", "true");

        final String configFile = args[0];

        log.info(configFile);

        RunSBB sbb = new RunSBB();

        sbb.prepare(configFile);
        sbb.run();
    }

    public void prepare(String configFile) {
        final Config config = this.loadConfig(configFile);
        Scenario scenario = ScenarioUtils.loadScenario(config);
        this.prepare(scenario);

    }

    public void prepare(Scenario scenario) {

        controler = new Controler(scenario);

        ScoringFunctionFactory scoringFunctionFactory = new SBBScoringFunctionFactory(scenario);
        controler.setScoringFunctionFactory(scoringFunctionFactory);

        postProcessing = new SBBPostProcessing(controler);

        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                addTravelTimeBinding("ride").to(networkTravelTime());
                addTravelDisutilityFactoryBinding("ride").to(carTravelDisutilityFactoryKey());

                addTravelTimeBinding("privateSFF").to(networkTravelTime());
                addTravelDisutilityFactoryBinding("privateSFF").to(carTravelDisutilityFactoryKey());
                install(new SBBQSimModule());

            }
        });

        Config config = scenario.getConfig();

        AccessTimeConfigGroup accessTimeConfigGroup = ConfigUtils.addOrGetModule(config, AccessTimeConfigGroup.GROUP_NAME, AccessTimeConfigGroup.class);
        if (accessTimeConfigGroup.getInsertingAccessEgressWalk()) {

            LocateAct locateAct = new LocateAct(accessTimeConfigGroup.getShapefile(), "GMDNAME");

            locateAct.fillCache(scenario.getPopulation());

            config.plansCalcRoute().setInsertingAccessEgressWalk(true);
            controler.addOverridingModule(new AbstractModule() {
                @Override
                public void install() {
                    addRoutingModuleBinding(TransportMode.car).toProvider(new SBBNetworkRouter(TransportMode.car, locateAct));
                    addRoutingModuleBinding(TransportMode.bike)
                            .toProvider(new SBBBeelineTeleportationRouting(config.plansCalcRoute().getModeRoutingParams().get(TransportMode.bike), locateAct));
                }

            });
        }


    }

    public Config loadConfig(String configFile) {
        return ConfigUtils.loadConfig(configFile, new PostProcessingConfigGroup(), new SBBTransitConfigGroup(), new AccessTimeConfigGroup(), new SBBBehaviorGroupsConfigGroup());
    }

    public Config createConfig() {
        return ConfigUtils.createConfig(new PostProcessingConfigGroup(), new SBBTransitConfigGroup(), new AccessTimeConfigGroup(), new SBBBehaviorGroupsConfigGroup());
    }

    public Controler getControler() {
        return this.controler;
    }

    public void run() {
        controler.run();
        postProcessing.write();
    }


}
