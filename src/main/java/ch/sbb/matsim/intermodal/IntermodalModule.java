package ch.sbb.matsim.intermodal;

import ch.sbb.matsim.config.SBBIntermodalConfigGroup;
import ch.sbb.matsim.routing.pt.raptor.RaptorIntermodalAccessEgress;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;


public class IntermodalModule extends AbstractModule {

    private SBBIntermodalConfigGroup configGroup;

    public IntermodalModule(Scenario scenario) {
        super(scenario.getConfig());
        this.configGroup = ConfigUtils.addOrGetModule(this.getConfig(), SBBIntermodalConfigGroup.class);
        this.prepare(scenario);

    }

    private void prepare(Scenario scenario) {
        Intermodal.prepareNetwork(scenario.getNetwork(), this.configGroup.getMode());
    }

    @Override
    public void install() {
        addTravelTimeBinding(this.configGroup.getMode()).to(networkTravelTime());
        addTravelDisutilityFactoryBinding(this.configGroup.getMode()).to(carTravelDisutilityFactoryKey());

        bind(RaptorIntermodalAccessEgress.class).toInstance(new SBBRaptorIntermodalAccessEgress(this.configGroup.getConstant(), this.configGroup.getMUTT(), this.configGroup.getWaitingtime()));

    }

}


