/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim;

import ch.sbb.matsim.analysis.SBBDefaultAnalysisListener;
import ch.sbb.matsim.analysis.SBBEventAnalysis;
import ch.sbb.matsim.analysis.convergence.ConvergenceConfigGroup;
import ch.sbb.matsim.analysis.convergence.ConvergenceStats;
import ch.sbb.matsim.analysis.linkAnalysis.IterationLinkAnalyzer;
import ch.sbb.matsim.analysis.modalsplit.ModalSplitStats;
import ch.sbb.matsim.analysis.tripsandlegsanalysis.*;
import ch.sbb.matsim.config.*;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.SamplesizeFactors;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.intermodal.IntermodalModule;
import ch.sbb.matsim.intermodal.analysis.SBBTransferAnalysisListener;
import ch.sbb.matsim.mobsim.qsim.SBBTransitModule;
import ch.sbb.matsim.mobsim.qsim.pt.SBBTransitEngineQSimModule;
import ch.sbb.matsim.preparation.*;
import ch.sbb.matsim.replanning.SBBPermissibleModesCalculator;
import ch.sbb.matsim.replanning.SBBSubtourModeChoice;
import ch.sbb.matsim.replanning.SBBTimeAllocationMutatorReRoute;
import ch.sbb.matsim.routing.SBBCapacityDependentInVehicleCostCalculator;
import ch.sbb.matsim.routing.access.AccessEgressModule;
import ch.sbb.matsim.routing.network.SBBNetworkRoutingConfigGroup;
import ch.sbb.matsim.routing.network.SBBNetworkRoutingModule;
import ch.sbb.matsim.routing.pt.raptor.RaptorInVehicleCostCalculator;
import ch.sbb.matsim.s3.S3Downloader;
import ch.sbb.matsim.scoring.SBBScoringFunctionFactory;
import ch.sbb.matsim.utils.ScenarioConsistencyChecker;
import ch.sbb.matsim.vehicles.CreateVehiclesFromType;
import ch.sbb.matsim.zones.ZonesModule;
import com.google.inject.Provides;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.analysis.TripsAndLegsCSVWriter;
import org.matsim.analysis.TripsAndLegsCSVWriter.CustomTripsWriterExtension;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
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
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.StageActivityHandling;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.utils.misc.OptionalTime;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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
			config.controler().setOutputDirectory(args[1]);
		}

		run(config);
	}

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
		createInitialEndTimeAttribute(scenario.getPopulation());
		ZonesModule.addZonestoScenario(scenario);
		SBBNetworkRoutingModule.prepareScenario(scenario);
		IntermodalModule.prepareIntermodalScenario(scenario);
		AccessEgressModule.prepareLinkAttributes(scenario, true);
		// vehicle types
		new CreateVehiclesFromType(scenario.getPopulation(), scenario.getVehicles(), "vehicleType", "car",
				scenario.getConfig().plansCalcRoute().getNetworkModes()).createVehicles();
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
				addControlerListenerBinding().to(SBBEventAnalysis.class);
				addControlerListenerBinding().to(SBBDefaultAnalysisListener.class);
				addPlanStrategyBinding("SBBTimeMutation_ReRoute").toProvider(SBBTimeAllocationMutatorReRoute.class);
				addPlanStrategyBinding(DefaultPlanStrategiesModule.DefaultStrategy.SubtourModeChoice).toProvider(SBBSubtourModeChoice.class);
				bind(PermissibleModesCalculator.class).to(SBBPermissibleModesCalculator.class).asEagerSingleton();
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
				bind(TripsAndLegsCSVWriter.CustomLegsWriterExtension.class).to(SBBLegsExtension.class);
				bind(TripsAndLegsCSVWriter.CustomTimeWriter.class).toInstance(v -> Long.toString((long) v));
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
		if (config.plansCalcRoute().getNetworkModes().contains(SBBModes.RIDE)) {
            // MATSim defines ride by default as teleported, which conflicts with the network mode
            config.plansCalcRoute().removeTeleportedModeParams(SBBModes.RIDE);
        }
		ActivityParamsBuilder.buildActivityParams(config);
		SamplesizeFactors.setFlowAndStorageCapacities(config);
	}

	public static void createInitialEndTimeAttribute(Population population) {
		for (Person p : population.getPersons().values()) {
			if (p.getAttributes().getAttribute(Variables.INIT_END_TIMES) != null) {
				continue;
			}
			Plan plan = p.getSelectedPlan();
			List<Activity> activities = TripStructureUtils.getActivities(plan, StageActivityHandling.ExcludeStageActivities);
			List<OptionalTime> endTimeList = new ArrayList<>();
			int i = 0;

			for (Activity act : activities) {
				if (i == activities.size() - 1) {
					break;
				}
				endTimeList.add(act.getEndTime());
				i += 1;
			}

			p.getAttributes()
					.putAttribute(Variables.INIT_END_TIMES, endTimeList
							.stream()
							.map(e -> e.isDefined() ? Double.toString(e.seconds()) : Variables.NO_INIT_END_TIME)
							.collect(Collectors.joining("_")));
		}
	}

	public static ConfigGroup[] getSbbDefaultConfigGroups() {
		return new ConfigGroup[]{new PostProcessingConfigGroup(), new SBBTransitConfigGroup(),
				new SBBBehaviorGroupsConfigGroup(), new SwissRailRaptorConfigGroup(),
				new ZonesListConfigGroup(), new ParkingCostConfigGroup(), new SBBIntermodalConfiggroup(), new SBBAccessTimeConfigGroup(),
				new SBBNetworkRoutingConfigGroup(), new SBBS3ConfigGroup(), new ConvergenceConfigGroup(), new SBBSupplyConfigGroup(), new SBBCapacityDependentRoutingConfigGroup(), new SBBReplanningConfigGroup()};
	}
}
