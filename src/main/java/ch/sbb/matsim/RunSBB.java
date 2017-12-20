/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim;


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
import ch.sbb.matsim.config.SBBBehaviorGroupsConfigGroup;
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

    public static void main(String[] args) {
        System.setProperty("matsim.preferLocalDtds", "true");

        final String configFile = args[0];

        log.info(configFile);

        final Config config = ConfigUtils.loadConfig(configFile, new PostProcessingConfigGroup(), new SBBTransitConfigGroup(),
                new SBBBehaviorGroupsConfigGroup(), new AccessTimeConfigGroup());

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


        new RunSBB().installAccessTime(controler);


        controler.run();
        postProcessing.write();
    }

    public RunSBB() {
    }


    public void installAccessTime(Controler controler) {

        Scenario scenario = controler.getScenario();

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
}
