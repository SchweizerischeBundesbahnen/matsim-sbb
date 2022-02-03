
/* *********************************************************************** *
 * project: org.matsim.*
 * PrepareForMobsimImpl.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2019 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package ch.sbb.matsim.utils;

import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import javax.inject.Inject;
import javax.inject.Provider;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.groups.GlobalConfigGroup;
import org.matsim.core.controler.PrepareForMobsim;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.population.algorithms.ParallelPersonAlgorithmUtils;
import org.matsim.core.population.algorithms.PersonPrepareForSim;
import org.matsim.core.population.algorithms.TripsToLegsAlgorithm;
import org.matsim.core.router.PlanRouter;
import org.matsim.core.router.TripRouter;
import org.matsim.core.utils.timing.TimeInterpretation;
import org.matsim.facilities.ActivityFacilities;

public final class MobiPerformancesTestPrepareForMobsim implements PrepareForMobsim {
	// I think it is ok to have this public final.  Since one may want to use it as a delegate.  kai, may'18
	// yyyyyy but how should that work with a non-public constructor? kai, jun'18
	// Well, I guess it can be injected as well?!
	// bind( PrepareForSimImpl.class ) ;
	// bind( PrepareForSim.class ).to( MyPrepareForSimImpl.class ) ;

	private static final Logger log = Logger.getLogger(MobiPerformancesTestPrepareForMobsim.class);

	private final Scenario scenario;
	private final Network network;
	private final Population population;
	private final ActivityFacilities activityFacilities;
	private final Provider<TripRouter> tripRouterProvider;

	@Inject
	MobiPerformancesTestPrepareForMobsim(GlobalConfigGroup globalConfigGroup, Scenario scenario, Network network,
			Population population, ActivityFacilities activityFacilities, Provider<TripRouter> tripRouterProvider) {
		this.scenario = scenario;
		this.network = network;
		this.population = population;
		this.activityFacilities = activityFacilities;
		this.tripRouterProvider = tripRouterProvider;
	}

	@Override
	public void run() {
		/*
		 * Create single-mode network here and hand it over to PersonPrepareForSim. Otherwise, each instance would create its
		 * own single-mode network. However, this assumes that the main mode is car - which PersonPrepareForSim also does. Should
		 * be probably adapted in a way that other main modes are possible as well. cdobler, oct'15.
		 * This is now only used for xy2links, which is the "street address" of the activity location of facility, and here for the time being we indeed
		 *  assume that it can be reached by car.  kai, jul'18
		 */
		final Network carOnlyNetwork;
		if (NetworkUtils.isMultimodal(network)) {
            log.info("Network seems to be multimodal. Create car-only network which is handed over to PersonPrepareForSim.");
            TransportModeNetworkFilter filter = new TransportModeNetworkFilter(network);
            carOnlyNetwork = NetworkUtils.createNetwork(scenario.getConfig());
            HashSet<String> modes = new HashSet<>();
            modes.add(TransportMode.car);
            filter.filter(carOnlyNetwork, modes);
        } else {
			carOnlyNetwork = network;
		}

		Map<Integer, Double> runtimes = new TreeMap<>();
		TripsToLegsAlgorithm tripsToLegsAlgorithm = new TripsToLegsAlgorithm(new SBBIntermodalAwareRouterModeIdentifier(scenario.getConfig()));

		for (int threads = 24; threads <= 144; threads = threads + 12) {

			// make sure all routes are calculated.
			var time = System.currentTimeMillis();
			ParallelPersonAlgorithmUtils.run(population, threads,
					() -> new PersonPrepareForSim(new PlanRouter(tripRouterProvider.get(), activityFacilities, TimeInterpretation.create(scenario.getConfig())), scenario,
							carOnlyNetwork)
			);
			double runTime = (System.currentTimeMillis() - time) / 1000.0;
			runtimes.put(threads, runTime);
			Logger.getLogger(getClass()).info("threads " + threads + " runtime " + runTime);
			scenario.getPopulation().getPersons().values().parallelStream().forEach(p -> tripsToLegsAlgorithm.run(p.getSelectedPlan()));
		}
		Logger.getLogger("Threads\tRuntime");
		runtimes.forEach((key, value) -> Logger.getLogger(getClass()).info(key + "\t" + value));
		System.exit(0);

	}

}
