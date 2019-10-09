/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim;


import ch.sbb.matsim.analysis.SBBPostProcessingOutputHandler;
import ch.sbb.matsim.config.*;
import ch.sbb.matsim.intermodal.IntermodalModule;
import ch.sbb.matsim.mobsim.qsim.SBBTransitModule;
import ch.sbb.matsim.mobsim.qsim.pt.SBBTransitEngineQSimModule;
import ch.sbb.matsim.plans.abm.AbmConverter;
import ch.sbb.matsim.preparation.PopulationSampler.SBBPopulationSampler;
import ch.sbb.matsim.replanning.SBBTimeAllocationMutatorReRoute;
import ch.sbb.matsim.routing.access.AccessEgress;
import ch.sbb.matsim.routing.pt.raptor.IntermodalRaptorStopFinder;
import ch.sbb.matsim.routing.pt.raptor.RaptorStopFinder;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import ch.sbb.matsim.s3.S3Downloader;
import ch.sbb.matsim.scoring.SBBScoringFunctionFactory;
import ch.sbb.matsim.vehicles.CreateVehiclesFromType;
import ch.sbb.matsim.vehicles.ParkingCostVehicleTracker;
import ch.sbb.matsim.vehicles.RideParkingCostTracker;
import ch.sbb.matsim.zones.ZonesModule;
import com.google.inject.Provides;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.mobsim.qsim.components.QSimComponentsConfig;
import org.matsim.core.mobsim.qsim.components.StandardQSimComponentConfigurator;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ScoringFunctionFactory;

/**
 * @author denism
 */
public class RunSBB {

    private final static Logger log = Logger.getLogger(RunSBB.class);
    public final static ConfigGroup[] sbbDefaultConfigGroups = {new PostProcessingConfigGroup(), new SBBTransitConfigGroup(),
            new SBBBehaviorGroupsConfigGroup(), new SBBPopulationSamplerConfigGroup(), new SwissRailRaptorConfigGroup(),
            new ZonesListConfigGroup(), new ParkingCostConfigGroup(), new SBBIntermodalConfigGroup(), new SBBAccessTimeConfigGroup()};


    public static void main(String[] args) {
        System.setProperty("matsim.preferLocalDtds", "true");

        final String configFile = args[0];
        log.info(configFile);
        final Config config = buildConfig(configFile);

        if (args.length > 1)
            config.controler().setOutputDirectory(args[1]);

        new S3Downloader(config);

        Scenario scenario = ScenarioUtils.loadScenario(config);
        addSBBDefaultScenarioModules(scenario);

        // controler
        Controler controler = new Controler(scenario);
        addSBBDefaultControlerModules(controler);
        controler.run();
    }

    public static void addSBBDefaultScenarioModules(Scenario scenario) {
        new AbmConverter().createInitialEndTimeAttribute(scenario.getPopulation());

        // vehicle types
        new CreateVehiclesFromType(scenario.getPopulation(), scenario.getVehicles(), "vehicleType", "car").createVehicles();
        scenario.getConfig().qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.fromVehiclesData);

        SBBPopulationSamplerConfigGroup samplerConfig = ConfigUtils.addOrGetModule(scenario.getConfig(), SBBPopulationSamplerConfigGroup.class);
        if (samplerConfig.getDoSample()) {
            SBBPopulationSampler sbbPopulationSampler = new SBBPopulationSampler();
            sbbPopulationSampler.sample(scenario.getPopulation(), samplerConfig.getFraction());
        }
    }

    public static void addSBBDefaultControlerModules(Controler controler) {
        Config config = controler.getConfig();
        Scenario scenario = controler.getScenario();
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
                addPlanStrategyBinding("SBBTimeMutation_ReRoute").toProvider(SBBTimeAllocationMutatorReRoute.class);

                addTravelTimeBinding("ride").to(networkTravelTime());
                addTravelDisutilityFactoryBinding("ride").to(carTravelDisutilityFactoryKey());

                install(new SBBTransitModule());
                install(new SwissRailRaptorModule());
                install(new ZonesModule());

                Config config = getConfig();
                ParkingCostConfigGroup parkCostConfig = ConfigUtils.addOrGetModule(config, ParkingCostConfigGroup.class);
                if (parkCostConfig.getZonesParkingCostAttributeName() != null && parkCostConfig.getZonesId() != null) {
                    addEventHandlerBinding().to(ParkingCostVehicleTracker.class);
                }
                if (parkCostConfig.getZonesRideParkingCostAttributeName() != null && parkCostConfig.getZonesId() != null) {
                    addEventHandlerBinding().to(RideParkingCostTracker.class);
                }
            }

            @Provides
            QSimComponentsConfig provideQSimComponentsConfig() {
                QSimComponentsConfig components = new QSimComponentsConfig();
                new StandardQSimComponentConfigurator(config).configure(components);
                SBBTransitEngineQSimModule.configure(components);
                return components;
            }
        });


        controler.addOverridingModule(new AccessEgress(scenario));
        controler.addOverridingModule(new IntermodalModule(scenario));
        controler.addOverridingModule(new AbstractModule() {
            @Override
            public void install() {
                this.bind(RaptorStopFinder.class).to(IntermodalRaptorStopFinder.class);
            }
        });

    }

    public static Config buildConfig(String filepath) {
        Config config = ConfigUtils.loadConfig(filepath, sbbDefaultConfigGroups);

        if (config.plansCalcRoute().getNetworkModes().contains(TransportMode.ride)) {
            // MATSim defines ride by default as teleported, which conflicts with the network mode
            config.plansCalcRoute().removeModeRoutingParams(TransportMode.ride);
        }

        return config;
    }
}
