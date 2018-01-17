package ch.sbb.matsim.routing.teleportation;

import javax.inject.Inject;
import javax.inject.Provider;

import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.router.RoutingModule;

import ch.sbb.matsim.analysis.LocateAct;

/**
 * Based on org.matsim.core.router.Teleportation
 *
 *
 */

public class SBBTeleportation implements Provider<RoutingModule> {

    private final PlansCalcRouteConfigGroup.ModeRoutingParams params;
    private LocateAct actLocator;

    public SBBTeleportation(PlansCalcRouteConfigGroup.ModeRoutingParams params, LocateAct locateAct) {
        this.params = params;
        this.actLocator = locateAct;

    }

    @Inject
    private PopulationFactory populationFactory;

    @Inject
    Network network;

    @Override
    public RoutingModule get() {
        return new SBBTeleportationRoutingModule(params.getMode(), populationFactory, params.getTeleportedModeSpeed(), params.getBeelineDistanceFactor(), this.actLocator, network);
    }

}

