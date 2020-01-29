
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

import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.matsim.api.core.v01.Id;
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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Based on org.matsim.core.router.NetworkRouting
 *
 *
 */
public class SBBNetworkRouting implements Provider<RoutingModule>

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

    @Inject
    ZonesCollection allZones;

    private Id<Zones> zonesId;

    public SBBNetworkRouting(String mode, Id<Zones> accessEgressZonesId) {
        this.mode = mode;
        this.zonesId = accessEgressZonesId;
    }

    private final String mode;

    @Override
    public RoutingModule get() {
        Zones zones = allZones.getZones(this.zonesId);
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
                    plansCalcRouteConfigGroup, zones);
        } else {
            // return DefaultRoutingModules.createPureNetworkRouter(mode, populationFactory, filteredNetwork, routeAlgo);
            throw new RuntimeException("You should not use this router or activate isInsertingAccessEgressWalk");
        }
    }
}

