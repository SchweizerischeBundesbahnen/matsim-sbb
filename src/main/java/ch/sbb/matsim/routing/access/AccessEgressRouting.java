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
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.facilities.Facility;
import org.opengis.feature.simple.SimpleFeature;

import ch.sbb.matsim.analysis.LocateAct;

public class AccessEgressRouting {
    private static final Logger log = Logger.getLogger(AccessEgressRouting.class);
    private LocateAct actLocator;
    private PopulationFactory populationFactory;
    private String stageActivityType;
    private String mode;
    private Network network;

    public AccessEgressRouting(LocateAct locateAct, PopulationFactory populationFactory, String mode, Network network) {
        this.network = network;
        this.actLocator = locateAct;
        this.populationFactory = populationFactory;
        this.mode = mode;
        this.stageActivityType = this.mode + " interaction";
    }

    public double addAccess(Facility fromFacility, Link accessActLink, double now, List<PlanElement> result, Person person) {

        if (fromFacility.getCoord() != null) { // otherwise the trip starts directly on the link; no need to bushwhack

            Coord accessActCoord = accessActLink.getToNode().getCoord();
            // yyyy think about better solution: this may generate long walks along the link.
            // (e.g. orthogonal projection)
            Gbl.assertNotNull(accessActCoord);

            Leg accessLeg = this.populationFactory.createLeg(TransportMode.access_walk);
            accessLeg.setDepartureTime(now);
            now += routeBushwhackingLeg(person, accessLeg, fromFacility.getCoord(), accessActCoord, now, accessActLink.getId(), accessActLink.getId());
            // yyyy might be possible to set the link ids to null. kai & dominik, may'16

            result.add(accessLeg);
            // log.warn( accessLeg );

            final Activity interactionActivity = createInteractionActivity(accessActCoord, accessActLink.getId());
            result.add(interactionActivity);
            // log.warn( interactionActivity );
        }
        return now;
    }


    public double addEgress(Facility toFacility, Link egressActLink, double now, List<PlanElement> result, Person person) {
        if (toFacility.getCoord() != null) { // otherwise the trip ends directly on the link; no need to bushwhack

            Coord egressActCoord = egressActLink.getToNode().getCoord();
            Gbl.assertNotNull(egressActCoord);

            final Activity interactionActivity = createInteractionActivity(egressActCoord, egressActLink.getId());
            result.add(interactionActivity);
            // log.warn( interactionActivity );

            Leg egressLeg = this.populationFactory.createLeg(TransportMode.egress_walk);
            egressLeg.setDepartureTime(now);
            now += routeBushwhackingLeg(person, egressLeg, egressActCoord, toFacility.getCoord(), now, egressActLink.getId(), egressActLink.getId());
            result.add(egressLeg);
            // log.warn( egressLeg );
        }
        // log.warn( "===" );
        return now;
    }

    private Activity createInteractionActivity(final Coord interactionCoord, final Id<Link> interactionLink) {
        Activity act = PopulationUtils.createActivityFromCoordAndLinkId(this.stageActivityType, interactionCoord, interactionLink);
        act.setMaximumDuration(0.0);
        return act;
    }


    private double routeBushwhackingLeg(Person person, Leg leg, Coord fromCoord, Coord toCoord, double depTime, Id<Link> dpLinkId, Id<Link> arLinkId) {
        // I don't think that it makes sense to use a RoutingModule for this, since that again makes assumptions about how to
        // map facilities, and if you follow through to the teleportation routers one even finds activity wrappers, which is yet another
        // complication which I certainly don't want here. kai, dec'15

        // dpLinkId, arLinkId need to be in Route for lots of code to function. So I am essentially putting in the "street address"
        // for completeness. Note that if we are walking to a parked car, this can be different from the car link id!! kai, dec'15

        // make simple assumption about distance and walking speed
        double dist = CoordUtils.calcEuclideanDistance(fromCoord, toCoord);

        // create an empty route, but with realistic travel time
        Route route = this.populationFactory.getRouteFactories().createRoute(Route.class, dpLinkId, arLinkId);

        double beelineDistanceFactor = 1.3;

        double estimatedNetworkDistance = dist * beelineDistanceFactor;

        SimpleFeature zone = this.actLocator.getZone(fromCoord);
        int travTime = 0;
        if (zone != null) {
            System.out.println(zone);
            travTime = (int) zone.getAttribute("ACC" + mode.toUpperCase());
        }
        route.setTravelTime(travTime);
        route.setDistance(estimatedNetworkDistance);
        leg.setRoute(route);
        leg.setDepartureTime(depTime);
        leg.setTravelTime(travTime);
        return travTime;
    }


    public Link decideOnLink(final Facility fromFacility) {
        Link accessActLink = null;
        if (fromFacility.getLinkId() != null) {
            accessActLink = this.network.getLinks().get(fromFacility.getLinkId());
            // i.e. if street address is in mode-specific subnetwork, I just use that, and do not search for another (possibly closer)
            // other link.

        }

        if (accessActLink == null) {
            // this is the case where the postal address link is NOT in the subnetwork, i.e. does NOT serve the desired mode,
            // OR the facility does not have a street address link in the first place.

            if (fromFacility.getCoord() == null) {
                throw new RuntimeException("access/egress bushwhacking leg not possible when neither facility link id nor facility coordinate given");
            }

            accessActLink = NetworkUtils.getNearestLink(this.network, fromFacility.getCoord());
            if (accessActLink == null) {
                int ii = 0;
                for (Link link : this.network.getLinks().values()) {
                    if (ii == 10) {
                        break;
                    }
                    ii++;
                    log.warn(link);
                }
            }
            Gbl.assertNotNull(accessActLink);
        }
        return accessActLink;
    }


    public StageActivityTypes getStageActivityTypes() {
        return new AccessEgressStageActivityTypes();
    }

    private final class AccessEgressStageActivityTypes implements StageActivityTypes {
        @Override
        public boolean isStageActivity(String activityType) {
            if (AccessEgressRouting.this.stageActivityType.equals(activityType)) {
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof AccessEgressStageActivityTypes)) {
                return false;
            }
            AccessEgressStageActivityTypes other = (AccessEgressStageActivityTypes) obj;
            return other.isStageActivity(AccessEgressRouting.this.stageActivityType);
        }

        @Override
        public int hashCode() {
            return AccessEgressRouting.this.stageActivityType.hashCode();
        }
    }

}
