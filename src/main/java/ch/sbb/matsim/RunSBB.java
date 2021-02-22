/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim;

import ch.sbb.matsim.analysis.SBBPostProcessingOutputHandler;
import ch.sbb.matsim.analysis.convergence.ConvergenceConfigGroup;
import ch.sbb.matsim.analysis.convergence.ConvergenceStats;
import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailDemandMatrixAggregator;
import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailDemandReporting;
import ch.sbb.matsim.analysis.tripsandlegsanalysis.RailTripsAnalyzer;
import ch.sbb.matsim.analysis.tripsandlegsanalysis.SBBTripsExtension;
import ch.sbb.matsim.config.ParkingCostConfigGroup;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.config.SBBAccessTimeConfigGroup;
import ch.sbb.matsim.config.SBBBehaviorGroupsConfigGroup;
import ch.sbb.matsim.config.SBBIntermodalConfiggroup;
import ch.sbb.matsim.config.SBBPopulationSamplerConfigGroup;
import ch.sbb.matsim.config.SBBS3ConfigGroup;
import ch.sbb.matsim.config.SBBTransitConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.ZonesListConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.intermodal.IntermodalModule;
import ch.sbb.matsim.intermodal.analysis.SBBTransferAnalysisListener;
import ch.sbb.matsim.mobsim.qsim.SBBTransitModule;
import ch.sbb.matsim.mobsim.qsim.pt.SBBTransitEngineQSimModule;
import ch.sbb.matsim.preparation.ActivityParamsBuilder;
import ch.sbb.matsim.preparation.LinkToFacilityAssigner;
import ch.sbb.matsim.preparation.PopulationSampler.SBBPopulationSampler;
import ch.sbb.matsim.preparation.PrepareActivitiesInPlans;
import ch.sbb.matsim.replanning.SBBPermissibleModesCalculator;
import ch.sbb.matsim.replanning.SBBTimeAllocationMutatorReRoute;
import ch.sbb.matsim.replanning.SimpleAnnealer;
import ch.sbb.matsim.replanning.SimpleAnnealerConfigGroup;
import ch.sbb.matsim.routing.access.AccessEgressModule;
import ch.sbb.matsim.routing.network.SBBNetworkRoutingConfigGroup;
import ch.sbb.matsim.routing.network.SBBNetworkRoutingModule;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorModule;
import ch.sbb.matsim.s3.S3Downloader;
import ch.sbb.matsim.scoring.SBBScoringFunctionFactory;
import ch.sbb.matsim.utils.ScenarioConsistencyChecker;
import ch.sbb.matsim.vehicles.CreateVehiclesFromType;
import ch.sbb.matsim.vehicles.ParkingCostVehicleTracker;
import ch.sbb.matsim.vehicles.RideParkingCostTracker;
import ch.sbb.matsim.zones.ZonesModule;
import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.log4j.Logger;
import org.matsim.analysis.TripsAndLegsCSVWriter.CustomTripsWriterExtension;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigGroup;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.mobsim.qsim.components.QSimComponentsConfig;
import org.matsim.core.mobsim.qsim.components.StandardQSimComponentConfigurator;
import org.matsim.core.population.algorithms.PermissibleModesCalculator;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.TripStructureUtils.StageActivityHandling;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.scoring.ScoringFunctionFactory;
import org.matsim.core.utils.misc.OptionalTime;

/**
 * @author denism
 */
public class RunSBB {

	public static final ConfigGroup[] sbbDefaultConfigGroups = {new PostProcessingConfigGroup(), new SBBTransitConfigGroup(),
			new SBBBehaviorGroupsConfigGroup(), new SBBPopulationSamplerConfigGroup(), new SwissRailRaptorConfigGroup(),
			new ZonesListConfigGroup(), new ParkingCostConfigGroup(), new SBBIntermodalConfiggroup(), new SBBAccessTimeConfigGroup(),
			new SBBNetworkRoutingConfigGroup(), new SimpleAnnealerConfigGroup(), new SBBS3ConfigGroup(), new ConvergenceConfigGroup()};
	private static final Logger log = Logger.getLogger(RunSBB.class);

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
		LinkToFacilityAssigner.run(scenario.getActivityFacilities(), scenario.getNetwork());
		PrepareActivitiesInPlans.overwriteActivitiesInPlans(scenario.getPopulation());
		createInitialEndTimeAttribute(scenario.getPopulation());
		ZonesModule.addZonestoScenario(scenario);
		SBBNetworkRoutingModule.prepareScenario(scenario);
		IntermodalModule.prepareIntermodalScenario(scenario);
		AccessEgressModule.prepareAccessEgressTimes(scenario);
		// vehicle types
		new CreateVehiclesFromType(scenario.getPopulation(), scenario.getVehicles(), "vehicleType", "car",
				scenario.getConfig().plansCalcRoute().getNetworkModes()).createVehicles();
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
				addControlerListenerBinding().to(SBBPostProcessingOutputHandler.class);
				addPlanStrategyBinding("SBBTimeMutation_ReRoute").toProvider(SBBTimeAllocationMutatorReRoute.class);
				bind(PermissibleModesCalculator.class).to(SBBPermissibleModesCalculator.class).asEagerSingleton();
				bind(RailTripsAnalyzer.class);
				bind(RailDemandMatrixAggregator.class);
				bind(RailDemandReporting.class);
				bind(CustomTripsWriterExtension.class).to(SBBTripsExtension.class);
				install(new SBBTransitModule());
				install(new SwissRailRaptorModule());
				install(new ZonesModule(scenario));
				install(new SBBNetworkRoutingModule());
				install(new AccessEgressModule());
				addControlerListenerBinding().to(SBBTransferAnalysisListener.class).asEagerSingleton();
				Config config = getConfig();
				ParkingCostConfigGroup parkCostConfig = ConfigUtils.addOrGetModule(config, ParkingCostConfigGroup.class);
				if (parkCostConfig.getZonesParkingCostAttributeName() != null && parkCostConfig.getZonesId() != null) {
					addEventHandlerBinding().to(ParkingCostVehicleTracker.class);
				}
				if (parkCostConfig.getZonesRideParkingCostAttributeName() != null && parkCostConfig.getZonesId() != null) {
					addEventHandlerBinding().to(RideParkingCostTracker.class);
				}

				SimpleAnnealerConfigGroup annealerConfig = ConfigUtils.addOrGetModule(config, SimpleAnnealerConfigGroup.class);
				if (annealerConfig.isActivateAnnealingModule()) {
					addControlerListenerBinding().to(SimpleAnnealer.class);
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
				SBBTransitEngineQSimModule.configure(components);
				return components;
			}
		});
		controler.addOverridingModule(new IntermodalModule());

	}

	public static Config buildConfig(String filepath) {
		Config config = ConfigUtils.loadConfig(filepath, sbbDefaultConfigGroups);
		adjustMobiConfig(config);
		config.checkConsistency();
		return config;
	}

	public static void adjustMobiConfig(Config config) {
		if (config.plansCalcRoute().getNetworkModes().contains(SBBModes.RIDE)) {
			// MATSim defines ride by default as teleported, which conflicts with the network mode
			config.plansCalcRoute().removeModeRoutingParams(SBBModes.RIDE);
		}
		ActivityParamsBuilder.buildActivityParams(config);
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
}
