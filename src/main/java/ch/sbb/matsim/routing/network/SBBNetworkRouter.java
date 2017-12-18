
/* *********************************************************************** *
 * project: org.matsim.*
 * TranitRouter.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2009 by the members listed in the COPYING,        *
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

package ch.sbb.matsim.routing.network;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.SingleModeNetworksCache;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.router.util.LeastCostPathCalculatorFactory;
import org.matsim.core.router.util.TravelTime;

import com.google.inject.Inject;
import com.google.inject.Provider;

import ch.sbb.matsim.analysis.LocateAct;

/**
 * Not thread-safe because MultiNodeDijkstra is not. Does not expect the TransitSchedule to change once constructed! michaz '13
 *
 * @author mrieser
 */
public class SBBNetworkRouter implements Provider<RoutingModule>

{

    @Inject
    Map<String, TravelTime> travelTimes;

    @Inject
    Map<String, TravelDisutilityFactory> travelDisutilityFactories;

    @Inject
    SingleModeNetworksCache singleModeNetworksCache;

    @Inject
    PlansCalcRouteConfigGroup plansCalcRouteConfigGroup;

    @Inject
    Network network;

    @Inject
    PopulationFactory populationFactory;

    @Inject
    LeastCostPathCalculatorFactory leastCostPathCalculatorFactory;

    private LocateAct actLocator;

    public SBBNetworkRouter(String mode, LocateAct actLocator) {
        this.mode = mode;
        this.actLocator = actLocator;

    }

    private final String mode;

    @Override
    public RoutingModule get() {
        Network filteredNetwork = null;

        // Ensure this is not performed concurrently by multiple threads!
        synchronized (this.singleModeNetworksCache.getSingleModeNetworksCache()) {
            filteredNetwork = this.singleModeNetworksCache.getSingleModeNetworksCache().get(mode);
            if (filteredNetwork == null) {
                TransportModeNetworkFilter filter = new TransportModeNetworkFilter(network);
                Set<String> modes = new HashSet<>();
                modes.add(mode);
                filteredNetwork = NetworkUtils.createNetwork();
                filter.filter(filteredNetwork, modes);
                this.singleModeNetworksCache.getSingleModeNetworksCache().put(mode, filteredNetwork);
            }
        }

        TravelDisutilityFactory travelDisutilityFactory = this.travelDisutilityFactories.get(mode);
        if (travelDisutilityFactory == null) {
            throw new RuntimeException("No TravelDisutilityFactory bound for mode " + mode + ".");
        }
        TravelTime travelTime = travelTimes.get(mode);
        if (travelTime == null) {
            throw new RuntimeException("No TravelTime bound for mode " + mode + ".");
        }
        LeastCostPathCalculator routeAlgo = leastCostPathCalculatorFactory.createPathCalculator(
                filteredNetwork,
                travelDisutilityFactory.createTravelDisutility(travelTime),
                travelTime);

        if (plansCalcRouteConfigGroup.isInsertingAccessEgressWalk()) {
            return new SBBNetworkRoutingInclAccessEgressModule(mode, populationFactory, filteredNetwork, routeAlgo,
                    plansCalcRouteConfigGroup, this.actLocator);
        } else {
            // return DefaultRoutingModules.createPureNetworkRouter(mode, populationFactory, filteredNetwork, routeAlgo);
            throw new RuntimeException("You should not use this router or activate isInsertingAccessEgressWalk");
        }
    }
}

