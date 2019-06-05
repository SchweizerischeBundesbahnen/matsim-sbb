package ch.sbb.matsim.routing.teleportation;

import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import org.matsim.api.core.v01.Id;
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
    private Id<Zones> zonesId;

    public SBBTeleportation(PlansCalcRouteConfigGroup.ModeRoutingParams params, Id<Zones> zonesId) {
        this.params = params;
        this.zonesId = zonesId;
    }

    @Inject private PopulationFactory populationFactory;

    @Inject private Network network;

    @Inject private ZonesCollection allZones;

    @Override
    public RoutingModule get() {
        Zones zones = this.allZones.getZones(this.zonesId);
        return new SBBTeleportationRoutingInclAccessEgressModule(params.getMode(), populationFactory, params.getTeleportedModeSpeed(), params.getBeelineDistanceFactor(), zones, network);
    }

}

