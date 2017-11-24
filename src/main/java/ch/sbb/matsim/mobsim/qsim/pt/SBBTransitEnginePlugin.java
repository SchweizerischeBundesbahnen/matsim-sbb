/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.mobsim.qsim.pt;

import com.google.inject.Module;
import org.matsim.core.config.Config;
import org.matsim.core.mobsim.framework.AgentSource;
import org.matsim.core.mobsim.qsim.AbstractQSimPlugin;
import org.matsim.core.mobsim.qsim.interfaces.DepartureHandler;
import org.matsim.core.mobsim.qsim.interfaces.MobsimEngine;
import org.matsim.core.mobsim.qsim.pt.SimpleTransitStopHandlerFactory;
import org.matsim.core.mobsim.qsim.pt.TransitStopHandlerFactory;

import java.util.Collection;
import java.util.Collections;

public class SBBTransitEnginePlugin extends AbstractQSimPlugin {

    public SBBTransitEnginePlugin(Config config) {
        super(config);
    }

    @Override
    public Collection<? extends Module> modules() {
        return Collections.singletonList(new com.google.inject.AbstractModule() {
            @Override
            protected void configure() {
                bind(SBBTransitQSimEngine.class).asEagerSingleton();
				bind(TransitStopHandlerFactory.class).to(SimpleTransitStopHandlerFactory.class).asEagerSingleton();
            }
        });
    }

    @Override
    public Collection<Class<? extends DepartureHandler>> departureHandlers() {
        return Collections.singletonList(SBBTransitQSimEngine.class);
    }

    @Override
    public Collection<Class<? extends AgentSource>> agentSources() {
        return Collections.singletonList(SBBTransitQSimEngine.class);
    }

    @Override
    public Collection<Class<? extends MobsimEngine>> engines() {
        return Collections.singletonList(SBBTransitQSimEngine.class);
    }
}
