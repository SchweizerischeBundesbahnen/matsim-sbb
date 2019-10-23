package ch.sbb.matsim.rideshare.analysis;

import org.matsim.core.controler.AbstractModule;

/**
 * @author jbischoff / SBB
 */
public class SBBDRTAnalysisModule extends AbstractModule {
    @Override
    public void install() {
        addControlerListenerBinding().to(RideshareAnalysisListener.class).asEagerSingleton();
        bind(ZonebasedRideshareAnalysis.class).asEagerSingleton();
    }
}
