package ch.sbb.matsim.plans.modules;

import ch.sbb.matsim.plans.MOBiPlansRunner;
import ch.sbb.matsim.plans.matrices.MOBiMatricesProvider;
import ch.sbb.matsim.plans.matrices.Matrices;
import ch.sbb.matsim.plans.models.MobilityToolOwnershipModel;
import ch.sbb.matsim.plans.models.ModelStep;
import ch.sbb.matsim.plans.models.PrimaryLocationChoiceModel;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.plans.models.formulas.ModeImpedanceFormulaRepository;
import ch.sbb.matsim.plans.models.utils.MOBiPlansOutputWriter;
import ch.sbb.matsim.plans.synpop.loading.SynpopLoader;
import ch.sbb.matsim.zones.Zones;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;
import com.google.inject.multibindings.Multibinder;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

public class DefaultMOBiPlansModule extends AbstractModule {
    final MOBiPlansConfig config;

    public DefaultMOBiPlansModule(MOBiPlansConfig config) {
        this.config = config;
    }

    @Override
    protected void configure() {
        super.configure();
        bind(MOBiPlansConfig.class).toInstance(config);
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        bind(Scenario.class).toInstance(scenario);
        //Zones, Matrices should be here immediately available after scenario creation
        bind(Zones.class).toProvider(MOBiZonesProvider.class).asEagerSingleton();

        //the following are lazy singletons
        bind(Matrices.class).toProvider(MOBiMatricesProvider.class);
        bind(ModeImpedanceFormulaRepository.class).toInstance(new ModeImpedanceFormulaRepository(config.data().modelBaseDir()+"/"+MOBiPlansConfig.MODE_IMPEDANCE_FORMULAS));
        Multibinder<ModelStep> multibinder = Multibinder.newSetBinder(binder(), ModelStep.class);

        multibinder.addBinding().to(SynpopLoader.class);
        multibinder.addBinding().to(MobilityToolOwnershipModel.class);
        multibinder.addBinding().to(PrimaryLocationChoiceModel.class);
        multibinder.addBinding().to(MOBiPlansOutputWriter.class);




    }
}
