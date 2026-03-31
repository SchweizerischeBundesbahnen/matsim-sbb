package ch.sbb.matsim.plans.modules;

import ch.sbb.matsim.plans.matrices.MOBiMatricesProvider;
import ch.sbb.matsim.plans.matrices.Matrices;
import ch.sbb.matsim.plans.models.steps.*;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.formulas.ModeImpedanceFormulaRepository;
import ch.sbb.matsim.plans.models.utils.MOBiPlansOutputWriter;
import ch.sbb.matsim.plans.models.utils.StrataPredicateFactory;
import ch.sbb.matsim.plans.synpop.loading.SynpopLoader;
import ch.sbb.matsim.zones.Zones;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;
import java.nio.file.Paths;

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
        //Zones, Matrices should be here immediately available after scenario creation
        bind(Zones.class).toProvider(MOBiZonesProvider.class).asEagerSingleton();
        bind(Matrices.class).toProvider(MOBiMatricesProvider.class).asEagerSingleton();

        //the following are lazy singletons
        bind(ModeImpedanceFormulaRepository.class).toInstance(new ModeImpedanceFormulaRepository(Paths.get(config.data().modelBaseDir()).resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_CHOICE_MODELS).resolve(MOBiPlansConfig.MODE_IMPEDANCE_FORMULAS).toString()));
        bind(StrataPredicateFactory.class).toInstance(new StrataPredicateFactory());
        bind(TourBuildingModel.class);
        bind(CombinedDestinationAndDurationChoiceModel.class);
        Multibinder<ModelStep> multibinder = Multibinder.newSetBinder(binder(), ModelStep.class);

        multibinder.addBinding().to(SynpopLoader.class);
        multibinder.addBinding().to(ZoneBasedAccessibilities.class);
        multibinder.addBinding().to(MobilityToolOwnershipModel.class);
        multibinder.addBinding().to(PrimaryLocationChoiceModel.class);
        multibinder.addBinding().to(MOBiPlansOutputWriter.class);
        multibinder.addBinding().to(PopulationSampler.class);
        multibinder.addBinding().to(IterativeDailyPlanGenerationOrchestrator.class);
        multibinder.addBinding().to(CalcZonalAttributes.class);




    }

    public Scenario getScenario() {
        return scenario;
    }
}
