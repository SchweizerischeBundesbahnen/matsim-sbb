package ch.sbb.matsim.plans.modules;

import ch.sbb.matsim.plans.matrices.MOBiMatricesProvider;
import ch.sbb.matsim.plans.matrices.Matrices;
import ch.sbb.matsim.plans.models.formulas.ModeImpedanceFormulaRepository;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.formulas.ModeImpedanceFormulaRepositoryFromCSV;
import ch.sbb.matsim.plans.models.steps.AddAdditionalTripVariables;
import ch.sbb.matsim.plans.models.steps.AssignFacilityLocations;
import ch.sbb.matsim.plans.models.steps.CalcZonalAttributes;
import ch.sbb.matsim.plans.models.steps.CombinedDestinationAndDurationChoiceModel;
import ch.sbb.matsim.plans.models.steps.HomeWorkPtAttributesGeneration;
import ch.sbb.matsim.plans.models.steps.InitialModeChoiceModel;
import ch.sbb.matsim.plans.models.steps.IterativeDailyPlanGenerationOrchestrator;
import ch.sbb.matsim.plans.models.steps.MOBiPlansOutputWriter;
import ch.sbb.matsim.plans.models.steps.MergeExogenousPlans;
import ch.sbb.matsim.plans.models.steps.MobilityToolOwnershipModel;
import ch.sbb.matsim.plans.models.steps.ModelStep;
import ch.sbb.matsim.plans.models.steps.PedestrianSpeedModel;
import ch.sbb.matsim.plans.models.steps.PopulationSampler;
import ch.sbb.matsim.plans.models.steps.PrimaryLocationChoiceModel;
import ch.sbb.matsim.plans.models.steps.RailaccessChoiceSet;
import ch.sbb.matsim.plans.models.steps.StatsWriter;
import ch.sbb.matsim.plans.models.steps.TourBuildingModel;
import ch.sbb.matsim.plans.models.steps.WorkFromHomeModel;
import ch.sbb.matsim.plans.models.steps.ZoneBasedAccessibilities;
import ch.sbb.matsim.plans.models.steps.demandstrata.DemandStrataRegistry;
import ch.sbb.matsim.plans.models.steps.demandstrata.StrataConfigLoader;
import ch.sbb.matsim.plans.models.steps.starttimechoice.StartTimeChoiceModel;
import ch.sbb.matsim.plans.models.steps.MOBiPlansOutputWriter;
import ch.sbb.matsim.plans.synpop.loading.SynpopLoader;
import ch.sbb.matsim.zones.Zones;
import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.multibindings.Multibinder;
import jakarta.inject.Singleton;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

public class DefaultMOBiPlansModule extends AbstractModule {
    final MOBiPlansConfig config;
    private final Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

    public DefaultMOBiPlansModule(MOBiPlansConfig config) {
        this.config = config;
    }

    @Override
    protected void configure() {
        super.configure();
        bind(MOBiPlansConfig.class).toInstance(config);
        bind(Scenario.class).toInstance(scenario);
        //Zones, Matrices and Strata definitions should be here immediately available after scenario creation
        bind(Zones.class).toProvider(MOBiZonesProvider.class).asEagerSingleton();
        bind(Matrices.class).toProvider(MOBiMatricesProvider.class).asEagerSingleton();
        bind(ModeImpedanceFormulaRepository.class).to(ModeImpedanceFormulaRepositoryFromCSV.class).asEagerSingleton();

        //the following are lazy singletons
        bind(TourBuildingModel.class);
        bind(CombinedDestinationAndDurationChoiceModel.class);
        Multibinder<ModelStep> multibinder = Multibinder.newSetBinder(binder(), ModelStep.class);

        multibinder.addBinding().to(SynpopLoader.class);
        multibinder.addBinding().to(ZoneBasedAccessibilities.class);
        multibinder.addBinding().to(MobilityToolOwnershipModel.class);
        multibinder.addBinding().to(PrimaryLocationChoiceModel.class);
        multibinder.addBinding().to(HomeWorkPtAttributesGeneration.class);
        multibinder.addBinding().to(MOBiPlansOutputWriter.class);
        multibinder.addBinding().to(PopulationSampler.class);
        multibinder.addBinding().to(IterativeDailyPlanGenerationOrchestrator.class);
        multibinder.addBinding().to(CalcZonalAttributes.class);
        multibinder.addBinding().to(InitialModeChoiceModel.class);
        multibinder.addBinding().to(StartTimeChoiceModel.class);
        multibinder.addBinding().to(AssignFacilityLocations.class);
        multibinder.addBinding().to(RailaccessChoiceSet.class);
        multibinder.addBinding().to(PedestrianSpeedModel.class);
        multibinder.addBinding().to(WorkFromHomeModel.class);
        multibinder.addBinding().to(StatsWriter.class);
        multibinder.addBinding().to(MergeExogenousPlans.class);
        multibinder.addBinding().to(AddAdditionalTripVariables.class);




    }

    public Scenario getScenario() {
        return scenario;
    }

    @Provides
    @Singleton
    public DemandStrataRegistry provideDemandStrataRegistry() {
        return StrataConfigLoader.createAndLoad(config);
    }
}
