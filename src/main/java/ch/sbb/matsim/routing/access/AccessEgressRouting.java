package ch.sbb.matsim.routing.access;

import java.util.List;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.population.Activity;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.StageActivityTypes;
import org.matsim.facilities.Facility;
import org.opengis.feature.simple.SimpleFeature;

import ch.sbb.matsim.analysis.LocateAct;

/**
 * Based on org.matsim.core.router.NetworkRoutingInclAccessEgressModule
 *
 * Extend a network leg with an access_walk and an egress_walk legs. Duration of access/egress is read from a shapefile.
 * The duration is a zone attribute. For the mode "car", this class read the column "ACCCAR". For identical zone, the access and egress times are equal.
 *
 */

public class AccessEgressRouting {
    private static final Logger log = Logger.getLogger(AccessEgressRouting.class);
    final private LocateAct actLocator;
    final private PopulationFactory populationFactory;
    final private String stageActivityType;
    final private String mode;
    final private Network network;
    final private String attribute;

    public AccessEgressRouting(final LocateAct locateAct, final PopulationFactory populationFactory, final String mode, final Network network) {
        this.network = network;
        this.actLocator = locateAct;
        this.populationFactory = populationFactory;
        this.mode = mode;
        this.stageActivityType = this.mode + " interaction";
        this.attribute = "ACC" + mode.toUpperCase(); // ?
    }

    public double addAccess(final Facility fromFacility, final Link accessActLink, double now, final List<PlanElement> result, final Person person) {

        final Leg accessLeg = this.populationFactory.createLeg(TransportMode.access_walk);
        accessLeg.setDepartureTime(now);
        now += routeBushwhackingLeg(person, accessLeg, fromFacility.getCoord(), now, accessActLink.getId(), accessActLink.getId());

        result.add(accessLeg);

        final Activity interactionActivity = createInteractionActivity(accessActLink);
        result.add(interactionActivity);
        return now;
    }


    public double addEgress(final Facility toFacility, final Link egressActLink, double now, final List<PlanElement> result, final Person person) {
        final Activity interactionActivity = createInteractionActivity(egressActLink);
        result.add(interactionActivity);

        final Leg egressLeg = this.populationFactory.createLeg(TransportMode.egress_walk);
        egressLeg.setDepartureTime(now);
        now += routeBushwhackingLeg(person, egressLeg, toFacility.getCoord(), now, egressActLink.getId(), egressActLink.getId());
        result.add(egressLeg);
        return now;
    }

    private Activity createInteractionActivity(final Link link) {
        final Coord coord = link.getToNode().getCoord();
        Gbl.assertNotNull(coord);
        final Activity act = PopulationUtils.createActivityFromCoordAndLinkId(this.stageActivityType, coord, link.getId());
        act.setMaximumDuration(0.0);
        return act;
    }


    private double routeBushwhackingLeg(final Person person, final Leg leg, final Coord coord, final double depTime, final Id<Link> dpLinkId, final Id<Link> arLinkId) {
        final Route route = this.populationFactory.getRouteFactories().createRoute(Route.class, dpLinkId, arLinkId);

        final SimpleFeature zone = this.actLocator.getZone(coord);
        int travTime = 0;

        if (zone != null) {
            travTime = (int) zone.getAttribute(attribute);
        }

        route.setTravelTime(travTime);

        route.setDistance(0);
        leg.setRoute(route);
        leg.setDepartureTime(depTime);
        leg.setTravelTime(travTime);
        return travTime;
    }


    public Link decideOnLink(final Facility fromFacility) {
        Link accessActLink = null;
        if (fromFacility.getLinkId() != null) {
            accessActLink = this.network.getLinks().get(fromFacility.getLinkId());
        }

        if (accessActLink == null) {
            if (fromFacility.getCoord() == null) {
                throw new RuntimeException("access/egress bushwhacking leg not possible when neither facility link id nor facility coordinate given");
            }

            accessActLink = NetworkUtils.getNearestLink(this.network, fromFacility.getCoord());
            Gbl.assertNotNull(accessActLink);
        }
        return accessActLink;
    }


    public StageActivityTypes getStageActivityTypes() {
        return new AccessEgressStageActivityTypes();
    }

    private final class AccessEgressStageActivityTypes implements StageActivityTypes {
        @Override
        public boolean isStageActivity(final String activityType) {
            return AccessEgressRouting.this.stageActivityType.equals(activityType);
        }

        @Override
        public boolean equals(final Object obj) {
            if (!(obj instanceof AccessEgressStageActivityTypes)) {
                return false;
            }
            final AccessEgressStageActivityTypes other = (AccessEgressStageActivityTypes) obj;
            return other.isStageActivity(AccessEgressRouting.this.stageActivityType);
        }

        @Override
        public int hashCode() {
            return AccessEgressRouting.this.stageActivityType.hashCode();
        }
    }

}
