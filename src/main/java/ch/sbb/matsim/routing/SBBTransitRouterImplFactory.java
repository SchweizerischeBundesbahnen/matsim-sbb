/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.routing;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.matsim.core.config.Config;
import org.matsim.pt.router.PreparedTransitSchedule;
import org.matsim.pt.router.TransitRouter;
import org.matsim.pt.router.TransitRouterConfig;
import org.matsim.pt.router.TransitRouterNetworkTravelTimeAndDisutility;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.router.TransitRouterNetwork;

/**
 * @author mrieser
 */
@Singleton
public class SBBTransitRouterImplFactory implements Provider<TransitRouter> {

	private final TransitRouterConfig config;
	private final TransitRouterNetwork routerNetwork;
	private final PreparedTransitSchedule preparedTransitSchedule;

	@Inject
    SBBTransitRouterImplFactory(final TransitSchedule schedule, final Config config) {
		this(schedule, new TransitRouterConfig(
				config.planCalcScore(),
				config.plansCalcRoute(),
				config.transitRouter(),
				config.vspExperimental()));
	}

	public SBBTransitRouterImplFactory(final TransitSchedule schedule, final TransitRouterConfig config) {
		this.config = config;
		this.routerNetwork = TransitRouterNetwork.createFromSchedule(schedule, this.config.getBeelineWalkConnectionDistance());
		this.preparedTransitSchedule = new PreparedTransitSchedule(schedule);
	}

	@Override
	public TransitRouter get() {

		TransitRouterNetworkTravelTimeAndDisutility ttCalculator = new TransitRouterNetworkTravelTimeAndDisutility(this.config, this.preparedTransitSchedule);
		return new SBBTransitRouterImpl(this.config, this.preparedTransitSchedule, this.routerNetwork, ttCalculator, ttCalculator);
	}
	
}
