package ch.sbb.matsim.routing.network;

import ch.sbb.matsim.routing.access.AccessEgressRouting;
import ch.sbb.matsim.zones.Zones;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.groups.PlansCalcRouteConfigGroup;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.routes.NetworkRoute;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.StageActivityTypes;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.facilities.Facility;

import java.util.ArrayList;
import java.util.List;

/**
 * Based on org.matsim.core.router.NetworkRoutingInclAccessEgressModule
 */
public final class SBBNetworkRoutingInclAccessEgressModule implements RoutingModule {

    private final String mode;
    private final PopulationFactory populationFactory;

    private final Network network;
    private final LeastCostPathCalculator routeAlgo;
    private AccessEgressRouting accessEgress;

    public SBBNetworkRoutingInclAccessEgressModule(
            final String mode,
            final PopulationFactory populationFactory,
            final Network network,
            final LeastCostPathCalculator routeAlgo,
            PlansCalcRouteConfigGroup calcRouteConfig, Zones zones) {

        //log.info("Using SBB Routing for mode: " + mode);

        this.accessEgress = new AccessEgressRouting(zones, populationFactory, mode, network);
        this.network = network;
        this.routeAlgo = routeAlgo;
        this.mode = mode;
        this.populationFactory = populationFactory;
        if (!calcRouteConfig.isInsertingAccessEgressWalk()) {
            throw new RuntimeException("trying to use access/egress but not switched on in config.  "
                    + "currently not supported; there are too many other problems");
        }
    }

    @Override
    public List<? extends PlanElement> calcRoute(
            final Facility fromFacility,
            final Facility toFacility,
            final double departureTime,
            final Person person) {

        Gbl.assertNotNull(fromFacility);
        Gbl.assertNotNull(toFacility);

        Link accessActLink = accessEgress.decideOnLink(fromFacility);
        Link egressActLink = accessEgress.decideOnLink(toFacility);

        double now = departureTime;

        List<PlanElement> result = new ArrayList<>();

        now = accessEgress.addAccess(fromFacility, accessActLink, now, result, person);

        // === compute the network leg:
        {
            Leg newLeg = this.populationFactory.createLeg(this.mode);
            newLeg.setDepartureTime(now);
            now += routeLeg(person, newLeg, accessActLink, egressActLink, now);

            result.add(newLeg);
            // log.warn( newLeg );
        }

        accessEgress.addEgress(toFacility, egressActLink, now, result, person);

        return result;
    }


    @Override
    public StageActivityTypes getStageActivityTypes() {
        return this.accessEgress.getStageActivityTypes();
    }

    @Override
    public String toString() {
        return "[NetworkRoutingModule: mode=" + this.mode + "]";
    }


    private double routeLeg(Person person, Leg leg, Link fromLink, Link toLink, double depTime) {
        double travTime;

        Node startNode = fromLink.getToNode(); // start at the end of the "current" link
        Node endNode = toLink.getFromNode(); // the target is the start of the link

        if (toLink != fromLink) { // (a "true" route)

            LeastCostPathCalculator.Path path = this.routeAlgo.calcLeastCostPath(startNode, endNode, depTime, person, null);
            if (path == null)
                throw new RuntimeException("No route found from node " + startNode.getId() + " to node " + endNode.getId() + ".");

            NetworkRoute route = this.populationFactory.getRouteFactories().createRoute(NetworkRoute.class, fromLink.getId(), toLink.getId());
            route.setLinkIds(fromLink.getId(), NetworkUtils.getLinkIds(path.links), toLink.getId());
            route.setTravelTime((int) path.travelTime);
            route.setTravelCost(path.travelCost);
            route.setDistance(RouteUtils.calcDistance(route, 1.0, 1.0, this.network));
            leg.setRoute(route);
            travTime = (int) path.travelTime;

        } else {
            // create an empty route == staying on place if toLink == endLink
            // note that we still do a route: someone may drive from one location to another on the link. kai, dec'15
            NetworkRoute route = this.populationFactory.getRouteFactories().createRoute(NetworkRoute.class, fromLink.getId(), toLink.getId());
            route.setTravelTime(0);
            route.setDistance(0.0);
            leg.setRoute(route);
            travTime = 0;
        }

        leg.setDepartureTime(depTime);
        leg.setTravelTime(travTime);

        return travTime;
    }

}
