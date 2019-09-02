package ch.sbb.matsim.intermodal;

import ch.sbb.matsim.config.SBBIntermodalConfigGroup;
import ch.sbb.matsim.config.SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet;
import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;

import java.util.HashSet;
import java.util.Set;


public class IntermodalModule extends AbstractModule {

    private SBBIntermodalConfigGroup configGroup;

    public IntermodalModule(Scenario scenario) {
        super(scenario.getConfig());
        this.configGroup = ConfigUtils.addOrGetModule(this.getConfig(), SBBIntermodalConfigGroup.class);
        this.prepare(scenario);

    }

    private void prepare(Scenario scenario) {
        for (SBBIntermodalModeParameterSet mode : this.configGroup.getModeParameterSets()) {
            if (mode.isOnNetwork()) {
                Intermodal.prepareNetwork(scenario.getNetwork(), mode.getMode());
                Set<String> mainModes = new HashSet<>(scenario.getConfig().qsim().getMainModes());
                mainModes.add(mode.getMode());
                scenario.getConfig().qsim().setMainModes(mainModes);
            }
        }
    }

    @Override
    public void install() {
        for (SBBIntermodalModeParameterSet mode : this.configGroup.getModeParameterSets()) {
            if (mode.isOnNetwork()) {
                addTravelTimeBinding(mode.getMode()).to(networkTravelTime());
                addTravelDisutilityFactoryBinding(mode.getMode()).to(carTravelDisutilityFactoryKey());
            }
        }

        bind(RaptorIntermodalAccessEgress.class).toInstance(new SBBRaptorIntermodalAccessEgress(this.configGroup.getModeParameterSets()));
    }

}


