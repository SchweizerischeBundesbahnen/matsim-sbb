/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim;

import ch.sbb.matsim.analysis.SBBDefaultAnalysisListener;
import ch.sbb.matsim.analysis.convergence.ConvergenceConfigGroup;
import ch.sbb.matsim.analysis.convergence.ConvergenceStats;
import ch.sbb.matsim.analysis.linkAnalysis.IterationLinkAnalyzer;
import ch.sbb.matsim.analysis.modalsplit.ModalSplitStats;
import ch.sbb.matsim.analysis.tripsandlegsanalysis.*;
import ch.sbb.matsim.config.*;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.SamplesizeFactors;
import ch.sbb.matsim.intermodal.IntermodalModule;
import ch.sbb.matsim.intermodal.analysis.SBBTransferAnalysisListener;
import ch.sbb.matsim.mavi.pt.TransferTimeChecker;
import ch.sbb.matsim.mobsim.qsim.SBBTransitModule;
import ch.sbb.matsim.mobsim.qsim.pt.SBBTransitEngineQSimModule;
import ch.sbb.matsim.preparation.*;
import ch.sbb.matsim.replanning.SBBPermissibleModesCalculator;
import ch.sbb.matsim.replanning.SBBSubtourModeChoice;
import ch.sbb.matsim.routing.SBBAnalysisMainModeIdentifier;
import ch.sbb.matsim.routing.SBBCapacityDependentInVehicleCostCalculator;
import ch.sbb.matsim.routing.access.AccessEgressModule;
import ch.sbb.matsim.routing.network.SBBNetworkRoutingConfigGroup;
import ch.sbb.matsim.routing.network.SBBNetworkRoutingModule;
import ch.sbb.matsim.routing.pt.raptor.RaptorInVehicleCostCalculator;
import ch.sbb.matsim.routing.pt.raptor.RaptorTransferCostCalculator;
import ch.sbb.matsim.routing.pt.raptor.SBBRaptorTransferCostCalculator;
import ch.sbb.matsim.s3.S3Downloader;
import ch.sbb.matsim.scoring.SBBScoringFunctionFactory;
import ch.sbb.matsim.utils.ScenarioConsistencyChecker;
import ch.sbb.matsim.vehicles.CreateVehiclesFromType;
import ch.sbb.matsim.zones.ZonesModule;
import com.google.inject.Provides;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.analysis.TripsAndLegsWriter;
import org.matsim.analysis.TripsAndLegsWriter.CustomTripsWriterExtension;
import org.matsim.api.core.v01.Scenario;
import org.matsim.contrib.parking.parkingcost.config.ParkingCostConfigGroup;
import org.matsim.contrib.parking.parkingcost.module.ParkingCostModule;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.mobsim.qsim.components.QSimComponentsConfig;
import org.matsim.core.mobsim.qsim.components.StandardQSimComponentConfigurator;
import org.matsim.core.population.algorithms.PermissibleModesCalculator;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.router.AnalysisMainModeIdentifier;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ScoringFunctionFactory;

/**
 * @author denism
 */
public class RunSBB {


	private static final Logger log = LogManager.getLogger(RunSBB.class);

	public static void main(String[] args) {
		System.setProperty("matsim.preferLocalDtds", "true");

		final String configFile = args[0];
		log.info(configFile);
		final Config config = buildConfig(configFile);

		if (args.length > 1) {
			config.controller().setOutputDirectory(args[1]);
		}
//		if (args.length > 2) {
//			prepareAutoCalibration(args, config);
//		}

		run(config);
	}

//	private static void prepareAutoCalibration(String[] args, Config config) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException {
//		String[] remainingArgs = Arrays.stream(args)
//				.skip(2)
//				.map(s -> s.replace("-c:", "--config:"))
//				.toArray(String[]::new);
//
//		ConfigUtils.applyCommandline(config, remainingArgs);
//
//		int idx = ArrayUtils.indexOf(args, "--params");
//		if (idx > -1) {
//			SBBBehaviorGroupsConfigGroup bgs = ConfigUtils.addOrGetModule(config, SBBBehaviorGroupsConfigGroup.class);
//			// Ensure that mode correction is present for all calibrated modes
//			for (SBBBehaviorGroupsConfigGroup.BehaviorGroupParams bg : bgs.getBehaviorGroupParams().values()) {
//				for (SBBBehaviorGroupsConfigGroup.PersonGroupValues values : bg.getPersonGroupByAttribute().values()) {
//					// All constant except for the fixed mode are reset
//					for (SBBBehaviorGroupsConfigGroup.ModeCorrection corr : values.getModeCorrectionParams().values()) {
//						if (!corr.getMode().equals("walk_main"))
//							corr.setConstant(0);
//					}
//					for (String m : List.of("car", "ride", "pt", "bike")) {
//						if (!values.getModeCorrectionParams().containsKey(m)) {
//							SBBBehaviorGroupsConfigGroup.ModeCorrection c = new SBBBehaviorGroupsConfigGroup.ModeCorrection();
//							c.setMode(m);
//							values.addModeCorrection(c);
//						}
//
//					}
//				}
//			}
//
//			// TODO: workaround to use private function, needs to be made public if stable
//			Method m = MATSimApplication.class.getDeclaredMethod("applySpecs", Config.class, Path.class);
//			m.setAccessible(true);
//			m.invoke(null, config, Path.of(args[idx + 1]));
//		}
//	}

	public static void run(Config config) {

		new S3Downloader(config);

		Scenario scenario = ScenarioUtils.loadScenario(config);
		addSBBDefaultScenarioModules(scenario);
		ScenarioConsistencyChecker.checkScenarioConsistency(scenario);
		// controler
		Controler controler = new Controler(scenario);
		addSBBDefaultControlerModules(controler);
		controler.run();
	}

	public static void addSBBDefaultScenarioModules(Scenario scenario) {
		LinkToFacilityAssigner.run(scenario.getActivityFacilities(), scenario.getNetwork(), scenario.getConfig());
		SBBXY2LinksAssigner.run(scenario.getPopulation(), scenario.getNetwork(), scenario.getConfig().network());
		LinkToStationsAssigner.runAssignment(scenario);
		NetworkMerger.mergeTransitNetworkFromSupplyConfig(scenario);
		PrepareActivitiesInPlans.overwriteActivitiesInPlans(scenario.getPopulation());
		ZonesModule.addZonestoScenario(scenario);
		TransferTimeChecker.addAdditionalTransferTimes(scenario);
		SBBNetworkRoutingModule.prepareScenario(scenario);
		IntermodalModule.prepareIntermodalScenario(scenario);
		AccessEgressModule.prepareLinkAttributes(scenario, true);
		// vehicle types
		new CreateVehiclesFromType(scenario.getPopulation(), scenario.getVehicles(), "vehicleType", "car",
				scenario.getConfig().routing().getNetworkModes()).createVehicles();
		scenario.getConfig().qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.fromVehiclesData);


	}

	public static void addSBBDefaultControlerModules(Controler controler) {
		Config config = controler.getConfig();

		Scenario scenario = controler.getScenario();
		ScoringFunctionFactory scoringFunctionFactory = new SBBScoringFunctionFactory(scenario);
		controler.setScoringFunctionFactory(scoringFunctionFactory);
		controler.addOverridingModule(new AbstractModule() {
			@Override
			public void install() {
				addControlerListenerBinding().to(SBBDefaultAnalysisListener.class);
				addPlanStrategyBinding(DefaultPlanStrategiesModule.DefaultStrategy.SubtourModeChoice).toProvider(SBBSubtourModeChoice.class);
				bind(PermissibleModesCalculator.class).to(SBBPermissibleModesCalculator.class).asEagerSingleton();
				bind(AnalysisMainModeIdentifier.class).to(SBBAnalysisMainModeIdentifier.class);
				bind(RaptorTransferCostCalculator.class).to(SBBRaptorTransferCostCalculator.class);
				bind(RailTripsAnalyzer.class);
				bind(DemandAggregator.class);
				bind(RailDemandReporting.class);
				bind(PtLinkVolumeAnalyzer.class);
				bind(PutSurveyWriter.class);
				bind(TripsAndDistanceStats.class);
				bind(ActivityWriter.class).asEagerSingleton();
				bind(ModalSplitStats.class);
				bind(IterationLinkAnalyzer.class).asEagerSingleton();
				bind(CustomTripsWriterExtension.class).to(SBBTripsExtension.class);
				bind(TripsAndLegsWriter.CustomLegsWriterExtension.class).to(SBBLegsExtension.class);
				bind(TripsAndLegsWriter.CustomTimeWriter.class).toInstance(v -> Long.toString((long) v));
				install(new SBBTransitModule());
				install(new ZonesModule(scenario));
				install(new SBBNetworkRoutingModule());
				install(new AccessEgressModule());
				addControlerListenerBinding().to(SBBTransferAnalysisListener.class).asEagerSingleton();
				Config config = getConfig();

				SBBCapacityDependentRoutingConfigGroup capacityDependentRoutingConfigGroup = ConfigUtils.addOrGetModule(config, ch.sbb.matsim.config.SBBCapacityDependentRoutingConfigGroup.class);
				boolean useServiceQuality = capacityDependentRoutingConfigGroup.getUseServiceQuality();
				final SBBCapacityDependentInVehicleCostCalculator inVehicleCostCalculator = useServiceQuality ? new SBBCapacityDependentInVehicleCostCalculator(capacityDependentRoutingConfigGroup.getMinimumCostFactor(), capacityDependentRoutingConfigGroup.getLowerCapacityLimit(), capacityDependentRoutingConfigGroup.getHighercapacitylimit(), capacityDependentRoutingConfigGroup.getMaximumCostFactor()) : null;
				log.info("SBB use service quality: " + useServiceQuality);
				if (useServiceQuality) {
					bind(RaptorInVehicleCostCalculator.class).toInstance(inVehicleCostCalculator);
				}
				ParkingCostConfigGroup parkCostConfig = ConfigUtils.addOrGetModule(config, ParkingCostConfigGroup.class);
				if (parkCostConfig.useParkingCost) {
					install(new ParkingCostModule());
				}

				ConvergenceConfigGroup convergenceStatsConfig = ConfigUtils.addOrGetModule(config, ConvergenceConfigGroup.class);
				if (convergenceStatsConfig.isActivateConvergenceStats()) {
					ConvergenceStats convergenceStats = new ConvergenceStats(this.getConfig());
					addControlerListenerBinding().toInstance(convergenceStats);
				}

			}

			@Provides
			QSimComponentsConfig provideQSimComponentsConfig() {
				QSimComponentsConfig components = new QSimComponentsConfig();
				new StandardQSimComponentConfigurator(config).configure(components);
				new SBBTransitEngineQSimModule().configure(components);
				return components;
			}
		});
		controler.addOverridingModule(new IntermodalModule());


	}

	public static Config buildConfig(String filepath) {
		Config config = ConfigUtils.loadConfig(filepath, getSbbDefaultConfigGroups());
		adjustMobiConfig(config);
		config.checkConsistency();
		return config;
	}

	public static void adjustMobiConfig(Config config) {
		if (config.routing().getNetworkModes().contains(SBBModes.RIDE)) {
			// MATSim defines ride by default as teleported, which conflicts with the network mode
			config.routing().removeTeleportedModeParams(SBBModes.RIDE);
		}
		ActivityParamsBuilder.buildActivityParams(config);
		SamplesizeFactors.setFlowAndStorageCapacities(config);
		XLSXScoringParser.buildScoringBehaviourGroups(config);
	}

	public static ConfigGroup[] getSbbDefaultConfigGroups() {
		return new ConfigGroup[]{new PostProcessingConfigGroup(), new SBBTransitConfigGroup(),
				new SBBBehaviorGroupsConfigGroup(), new SwissRailRaptorConfigGroup(),
				new ZonesListConfigGroup(), new ParkingCostConfigGroup(), new SBBIntermodalConfiggroup(),
				new SBBAccessTimeConfigGroup(), new SBBNetworkRoutingConfigGroup(), new SBBS3ConfigGroup(),
				new ConvergenceConfigGroup(), new SBBSupplyConfigGroup(), new SBBCapacityDependentRoutingConfigGroup(),
				new SBBReplanningConfigGroup(), new SBBScoringParametersConfigGroup()};
	}
}
