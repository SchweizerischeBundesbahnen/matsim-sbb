package ch.sbb.matsim.routing.teleportation;

import javax.inject.Inject;
import javax.inject.Provider;

import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.router.RoutingModule;

import ch.sbb.matsim.analysis.LocateAct;
import ch.sbb.matsim.config.AccessTimeConfigGroup;

public class SBBBeelineTeleportationRouting implements Provider<RoutingModule> {

    private final PlansCalcRouteConfigGroup.ModeRoutingParams params;
    private LocateAct actLocator;

    public SBBBeelineTeleportationRouting(PlansCalcRouteConfigGroup.ModeRoutingParams params, AccessTimeConfigGroup accessTimeConfigGroup) {
        this.params = params;
        if (accessTimeConfigGroup.getInsertingAccessEgressWalk()) {
            this.actLocator = new LocateAct(accessTimeConfigGroup.getShapefile(), "GMDNAME");
        }

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

