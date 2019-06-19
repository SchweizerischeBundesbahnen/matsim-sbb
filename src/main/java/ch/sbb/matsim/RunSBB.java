/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim;


import ch.ethz.matsim.utils.CommandLine;
import ch.ethz.matsim.utils.CommandLine.ConfigurationException;
import ch.ethz.matsim.discrete_mode_choice.modules.ConstraintModule;
import ch.ethz.matsim.discrete_mode_choice.modules.DiscreteModeChoiceConfigurator;
import ch.ethz.matsim.discrete_mode_choice.modules.DiscreteModeChoiceModule;
import ch.ethz.matsim.discrete_mode_choice.modules.EstimatorModule;
import ch.ethz.matsim.discrete_mode_choice.modules.SelectorModule;
import ch.ethz.matsim.discrete_mode_choice.modules.config.DiscreteModeChoiceConfigGroup;
import ch.sbb.matsim.analysis.SBBPostProcessingOutputHandler;
import ch.sbb.matsim.config.*;
import ch.sbb.matsim.mobsim.qsim.SBBTransitModule;
import ch.sbb.matsim.mobsim.qsim.pt.SBBTransitEngineQSimModule;
import ch.sbb.matsim.plans.abm.AbmConverter;
import ch.sbb.matsim.preparation.PopulationSampler.SBBPopulationSampler;
import ch.sbb.matsim.replanning.SBBTimeAllocationMutatorReRoute;
import ch.sbb.matsim.routing.access.AccessEgress;
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
import org.matsim.core.config.Config;
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
 *
 */
public class RunSBB {

    private final static Logger log = Logger.getLogger(RunSBB.class);

    public static void main(String[] args) throws ConfigurationException {
    	
    	
    	
    	CommandLine cmd = new CommandLine.Builder(args)
                .allowOptions("configPath", "output", "iterations", "mcMode", "useEstimates", "stMC", "lMC", "dMC", "selectionMode", "tripEstimationMode", "tourEstimationMode", "innovationTurnoffFraction", "nrPeopleToKeep", "resetPlans")
                .build();

        final String configFile = cmd.getOption("configPath").orElse("..\\input\\CNB\\config\\config_parsed.xml");
        String outputPath = cmd.getOption("output").orElse("output_sbb_dmc");
        int iterations = cmd.getOption("iterations").map(Integer::parseInt).orElse(10);
        double stMC = cmd.getOption("stMC").map(Double::parseDouble).orElse(0.0); // subtourModeChoice
        double dMC = cmd.getOption("dMC").map(Double::parseDouble).orElse(0.0); // DiscreteModeChoice
        double innovationTurnoffFraction = cmd.getOption("innovationTurnoffFraction").map(Double::parseDouble).orElse(0.7);
        String selectionMode = cmd.getOption("selectionMode").orElse(SelectorModule.RANDOM);
        String tripEstimationMode = cmd.getOption("tripEstimationMode").orElse(EstimatorModule.UNIFORM);
        String tourEstimationMode = cmd.getOption("tourEstimationMode").orElse(EstimatorModule.UNIFORM);
        int nrPeopleToKeep = cmd.getOption("nrPeopleToKeep").map(Integer::parseInt).orElse(-1);
        boolean resetPlans = cmd.getOption("resetPlans").map(Boolean::parseBoolean).orElse(false);
    	
    	
    	
        System.setProperty("matsim.preferLocalDtds", "true");

        /*final String configFile = args[0];*/
        log.info(configFile);
        final Config config = buildConfig(configFile);

        config.controler().setOutputDirectory(outputPath);

        new S3Downloader(config);

        Scenario scenario = ScenarioUtils.loadScenario(config);
        scenario.getConfig().controler().setLastIteration(iterations);
        
        new AbmConverter().createInitialEndTimeAttribute(scenario.getPopulation());

        // vehicle types
        new CreateVehiclesFromType(scenario.getPopulation(), scenario.getVehicles(), "vehicleType", "car").createVehicles();
        scenario.getConfig().qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.fromVehiclesData);

        // controler
        Controler controler = new Controler(scenario);

        if (dMC > 0.0) {
            controler.addOverridingModule(new DiscreteModeChoiceModule());
            if (SelectorModule.RANDOM.equals(selectionMode)) {
                DiscreteModeChoiceConfigurator.configureAsSubtourModeChoiceReplacement(config);
            } else {
                DiscreteModeChoiceConfigurator.configureAsImportanceSampler(config);
            }
            DiscreteModeChoiceConfigGroup dmcConfig = (DiscreteModeChoiceConfigGroup) config.getModules().get(DiscreteModeChoiceConfigGroup.GROUP_NAME);
            dmcConfig.setTourConstraintsAsString(ConstraintModule.SUBTOUR_MODE);
        }

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

        controler.run();
    }

    public static Config buildConfig(String filepath) {
        return ConfigUtils.loadConfig(filepath, new PostProcessingConfigGroup(), new SBBTransitConfigGroup(),
                new SBBBehaviorGroupsConfigGroup(), new SBBPopulationSamplerConfigGroup(), new SwissRailRaptorConfigGroup(),
                new ZonesListConfigGroup(), new ParkingCostConfigGroup());
    }
}
