/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.routing;

import org.matsim.core.controler.AbstractModule;
import org.matsim.pt.router.TransitRouter;

public class SBBTransitRouterModule extends AbstractModule {

    @Override
    public void install() {
        if (getConfig().transit().isUseTransit()) {
            bind(TransitRouter.class).toProvider(SBBTransitRouterImplFactory.class);
        }
    }

}
