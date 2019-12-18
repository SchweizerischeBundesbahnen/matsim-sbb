package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.config.SBBIntermodalConfigGroup;
import ch.sbb.matsim.config.SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet;
import ch.sbb.matsim.config.variables.SBBModes;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.facilities.Facility;
import org.matsim.pt.transitSchedule.api.MinimalTransferTimes;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author mrieser / Simunto GmbH
 */
public class SBBIntermodalRaptorStopFinder implements RaptorStopFinder {

    private final static Logger log = Logger.getLogger(SBBIntermodalRaptorStopFinder.class);

    private final RaptorIntermodalAccessEgress intermodalAE;
    private final List<SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet> intermodalModeParams;
    private final Map<String, RoutingModule> routingModules;
    private final TransitSchedule transitSchedule;
    private final Set<String> networkModes;

    @Inject
    public SBBIntermodalRaptorStopFinder(Config config, RaptorIntermodalAccessEgress intermodalAE,
                                         Map<String, Provider<RoutingModule>> routingModuleProviders,
                                         TransitSchedule transitSchedule) {
        this.intermodalAE = intermodalAE;
        this.transitSchedule = transitSchedule;

        SBBIntermodalConfigGroup intermodalConfigGroup = ConfigUtils.addOrGetModule(config, SBBIntermodalConfigGroup.class);
        this.intermodalModeParams = intermodalConfigGroup.getModeParameterSets();

        SwissRailRaptorConfigGroup srrConfig = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class);
        this.routingModules = new HashMap<>();
        this.networkModes = new HashSet<>();
        if (srrConfig.isUseIntermodalAccessEgress()) {
            for (SBBIntermodalModeParameterSet params : this.intermodalModeParams) {
                String mode = params.getMode();
                if (params.isRoutedOnNetwork()) {
                    this.networkModes.add(mode);
                } else {
                    this.routingModules.put(mode, routingModuleProviders.get(mode).get());
                }
            }
        }
    }

    @Override
    public List<InitialStop> findStops(Facility facility, Person person, double departureTime, RaptorParameters parameters, SwissRailRaptorData data, RaptorStopFinder.Direction type) {
        if (type == Direction.ACCESS) {
            return findAccessStops(facility, person, departureTime, parameters, data);
        }
        if (type == Direction.EGRESS) {
            return findEgressStops(facility, person, departureTime, parameters, data);
        }
        return Collections.emptyList();
    }

    private List<InitialStop> findAccessStops(Facility facility, Person person, double departureTime, RaptorParameters parameters, SwissRailRaptorData data) {
        SwissRailRaptorConfigGroup srrCfg = parameters.getConfig();
        if (srrCfg.isUseIntermodalAccessEgress()) {
            return findIntermodalStops(facility, person, departureTime, Direction.ACCESS, parameters, data);
        } else {
            double distanceFactor = data.config.getBeelineWalkDistanceFactor();
            List<TransitStopFacility> stops = findNearbyStops(facility, parameters, data);
            return stops.stream().map(stop -> {
                double beelineDistance = CoordUtils.calcEuclideanDistance(stop.getCoord(), facility.getCoord());
                double travelTime = Math.ceil(beelineDistance / parameters.getBeelineWalkSpeed());
                double disutility = travelTime * -parameters.getMarginalUtilityOfTravelTime_utl_s(TransportMode.non_network_walk);
                return new InitialStop(stop, disutility, travelTime, beelineDistance * distanceFactor, TransportMode.non_network_walk);
            }).collect(Collectors.toList());
        }
    }

    private List<InitialStop> findEgressStops(Facility facility, Person person, double departureTime, RaptorParameters parameters, SwissRailRaptorData data) {
        SwissRailRaptorConfigGroup srrCfg = parameters.getConfig();
        if (srrCfg.isUseIntermodalAccessEgress()) {
            return findIntermodalStops(facility, person, departureTime, Direction.EGRESS, parameters, data);
        } else {
            double distanceFactor = data.config.getBeelineWalkDistanceFactor();
            List<TransitStopFacility> stops = findNearbyStops(facility, parameters, data);
            return stops.stream().map(stop -> {
                double beelineDistance = CoordUtils.calcEuclideanDistance(stop.getCoord(), facility.getCoord());
                double travelTime = Math.ceil(beelineDistance / parameters.getBeelineWalkSpeed());
                double disutility = travelTime * -parameters.getMarginalUtilityOfTravelTime_utl_s(TransportMode.non_network_walk);
                return new InitialStop(stop, disutility, travelTime, beelineDistance * distanceFactor, TransportMode.non_network_walk);
            }).collect(Collectors.toList());
        }
    }

    private List<InitialStop> findIntermodalStops(Facility facility, Person person, double departureTime, Direction direction, RaptorParameters parameters, SwissRailRaptorData data) {
        SwissRailRaptorConfigGroup srrCfg = parameters.getConfig();
        double x = facility.getCoord().getX();
        double y = facility.getCoord().getY();
        List<InitialStop> initialStops = new ArrayList<>();
        for (IntermodalAccessEgressParameterSet paramset : srrCfg.getIntermodalAccessEgressParameterSets()) {
            double radius = paramset.getMaxRadius();
            String mode = paramset.getMode();

            if (personMatches(person, paramset)) {
                Collection<TransitStopFacility> stopFacilities = data.stopsQT.getDisk(x, y, radius);
                boolean isNetworkMode = this.networkModes.contains(mode);
                if (isNetworkMode) {
                    // TODO
                } else {
                    calculateIndividualRoutesToStops(stopFacilities, paramset, parameters, person, direction, mode, facility, departureTime, initialStops);
                }
            }
        }

        return initialStops;
    }

    private void calculateIndividualRoutesToStops(Collection<TransitStopFacility> stopFacilities, IntermodalAccessEgressParameterSet paramset, RaptorParameters parameters,
                                                  Person person, Direction direction, String mode, Facility facility, double departureTime, List<InitialStop> initialStops) {
        boolean useMinimalTransferTimes = doUseMinimalTransferTimes(mode);
        String overrideMode = null;
        if (mode.equals(SBBModes.WALK) || mode.equals(SBBModes.PT_FALLBACK_MODE)) {
            overrideMode = SBBModes.NON_NETWORK_WALK;
        }
        String linkIdAttribute = paramset.getLinkIdAttribute();

        for (TransitStopFacility stop : stopFacilities) {
            if (stopMatches(stop, paramset)) {
                Facility stopFacility = stop;
                if (linkIdAttribute != null) {
                    Object attr = stop.getAttributes().getAttribute(linkIdAttribute);
                    if (attr != null) {
                        stopFacility = new ChangedLinkFacility(stop, Id.create(attr.toString(), Link.class));
                    }
                }

                List<? extends PlanElement> routeParts;
                if (direction == Direction.ACCESS) {
                    RoutingModule module = this.routingModules.get(mode);
                    routeParts = module.calcRoute(facility, stopFacility, departureTime, person);
                } else { // it's Egress
                    // We don't know the departure time for the egress trip, so just use the original departureTime,
                    // although it is wrong and might result in a wrong traveltime and thus wrong route.
                    RoutingModule module = this.routingModules.get(mode);
                    routeParts = module.calcRoute(stopFacility, facility, departureTime, person);
                    // clear the (wrong) departureTime so users don't get confused
                    for (PlanElement pe : routeParts) {
                        if (pe instanceof Leg) {
                            ((Leg) pe).setDepartureTime(Time.getUndefinedTime());
                        }
                    }
                }
                if (overrideMode != null) {
                    for (PlanElement pe : routeParts) {
                        if (pe instanceof Leg) {
                            ((Leg) pe).setMode(overrideMode);
                        }
                    }
                }
                if (stopFacility != stop) {
                    if (direction == Direction.ACCESS) {
                        Leg transferLeg = createTransferLeg(stopFacility.getLinkId(), stop.getLinkId(), useMinimalTransferTimes, stop);

                        List<PlanElement> tmp = new ArrayList<>(routeParts.size() + 1);
                        tmp.addAll(routeParts);
                        tmp.add(transferLeg);
                        routeParts = tmp;
                    } else {
                        Leg transferLeg = createTransferLeg(stop.getLinkId(), stopFacility.getLinkId(), useMinimalTransferTimes, stop);

                        List<PlanElement> tmp = new ArrayList<>(routeParts.size() + 1);
                        tmp.add(transferLeg);
                        tmp.addAll(routeParts);
                        routeParts = tmp;
                    }
                }
                RaptorIntermodalAccessEgress.RIntermodalAccessEgress accessEgress = this.intermodalAE.calcIntermodalAccessEgress(routeParts, parameters, person);
                InitialStop iStop = new InitialStop(stop, accessEgress.disutility, accessEgress.travelTime, accessEgress.routeParts);
                initialStops.add(iStop);
            }
        }
    }

    private boolean personMatches(Person person, IntermodalAccessEgressParameterSet paramset) {
        String personFilterAttribute = paramset.getPersonFilterAttribute();
        String personFilterValue = paramset.getPersonFilterValue();

        boolean personMatches = true;
        if (personFilterAttribute != null) {
            Object attr = person.getAttributes().getAttribute(personFilterAttribute);
            String attrValue = attr == null ? null : attr.toString();
            personMatches = personFilterValue.equals(attrValue);
        }
        return personMatches;
    }

    private boolean stopMatches(TransitStopFacility stop, IntermodalAccessEgressParameterSet paramset) {
        String stopFilterAttribute = paramset.getStopFilterAttribute();
        String stopFilterValue = paramset.getStopFilterValue();

        boolean filterMatches = true;
        if (stopFilterAttribute != null) {
            Object attr = stop.getAttributes().getAttribute(stopFilterAttribute);
            String attrValue = attr == null ? null : attr.toString();
            filterMatches = stopFilterValue.equals(attrValue);
        }
        return filterMatches;
    }

    private boolean doUseMinimalTransferTimes(String mode) {
        for (SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet modeParams : this.intermodalModeParams) {
            if (mode.equals(modeParams.getMode())) {
                return modeParams.doUseMinimalTransferTimes();
            }
        }
        return false;
    }

    private double getMinimalTransferTime(TransitStopFacility stop) {
        MinimalTransferTimes mtt = this.transitSchedule.getMinimalTransferTimes();
        double transferTime = mtt.get(stop.getId(), stop.getId());
        if (transferTime == Double.NaN) {
            // return a default value of 30 seconds
            return 30.0;
        }
        else    {
            return transferTime;
        }
    }

    private List<TransitStopFacility> findNearbyStops(Facility facility, RaptorParameters parameters, SwissRailRaptorData data) {
        double x = facility.getCoord().getX();
        double y = facility.getCoord().getY();
        Collection<TransitStopFacility> stopFacilities = data.stopsQT.getDisk(x, y, parameters.getSearchRadius());
        if (stopFacilities.size() < 2) {
            TransitStopFacility  nearestStop = data.stopsQT.getClosest(x, y);
            double nearestDistance = CoordUtils.calcEuclideanDistance(facility.getCoord(), nearestStop.getCoord());
            stopFacilities = data.stopsQT.getDisk(x, y, nearestDistance + parameters.getExtensionRadius());
        }
        if (stopFacilities instanceof List) {
            return (List<TransitStopFacility>) stopFacilities;
        }
        return new ArrayList<>(stopFacilities);
    }

    private Leg createTransferLeg(Id<Link> fromLinkId, Id<Link> toLinkId, boolean useMinimalTransferTimes, TransitStopFacility stop) {
        Leg transferLeg = PopulationUtils.createLeg(SBBModes.NON_NETWORK_WALK);
        Route transferRoute = RouteUtils.createGenericRouteImpl(fromLinkId, toLinkId);
        double transferTime = 0.0;
        if (useMinimalTransferTimes) {
            transferTime = this.getMinimalTransferTime(stop);
        }
        transferRoute.setTravelTime(transferTime);
        transferRoute.setDistance(0);
        transferLeg.setRoute(transferRoute);
        transferLeg.setTravelTime(transferTime);
        return transferLeg;
    }

    private static class ChangedLinkFacility implements Facility, Identifiable<TransitStopFacility> {

        private final TransitStopFacility delegate;
        private final Id<Link> linkId;

        ChangedLinkFacility(final TransitStopFacility delegate, final Id<Link> linkId) {
            this.delegate = delegate;
            this.linkId = linkId;
        }

        @Override
        public Id<Link> getLinkId() {
            return this.linkId;
        }

        @Override
        public Coord getCoord() {
            return this.delegate.getCoord();
        }

        @Override
        public Map<String, Object> getCustomAttributes() {
            return this.delegate.getCustomAttributes();
        }

        @Override
        public Id<TransitStopFacility> getId() {
            return this.delegate.getId();
        }
    }
}
