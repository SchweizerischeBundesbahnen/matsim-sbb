package ch.sbb.matsim.routing.teleportation;

import ch.sbb.matsim.zones.Zones;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.router.RoutingModule;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Based on org.matsim.core.router.Teleportation
 *
 *
 */

public class SBBTeleportation implements Provider<RoutingModule> {

    private final PlansCalcRouteConfigGroup.ModeRoutingParams params;
    private Zones zones;

    public SBBTeleportation(PlansCalcRouteConfigGroup.ModeRoutingParams params, Zones zones) {
        this.params = params;
        this.zones = zones;

    }

    @Inject
    private PopulationFactory populationFactory;

    @Inject
    Network network;

    @Override
    public RoutingModule get() {
        return new SBBTeleportationRoutingInclAccessEgressModule(params.getMode(), populationFactory, params.getTeleportedModeSpeed(), params.getBeelineDistanceFactor(), this.zones, network);
    }

}

