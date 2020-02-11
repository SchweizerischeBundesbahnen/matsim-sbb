package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.config.SBBIntermodalConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet;
import ch.sbb.matsim.config.variables.SBBModes;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
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
    private final Map<String, SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet> intermodalModeParams;
    private final Map<String, RoutingModule> routingModules;
    private final TransitSchedule transitSchedule;
    private final Random random = MatsimRandom.getLocalInstance();
    private final AccessEgressRouteCache accessEgressRouteCache;

    @Inject
    public SBBIntermodalRaptorStopFinder(Config config, RaptorIntermodalAccessEgress intermodalAE,
                                         Map<String, Provider<RoutingModule>> routingModuleProviders,
                                         TransitSchedule transitSchedule, AccessEgressRouteCache accessEgressRouteCache) {
        this.intermodalAE = intermodalAE;
        this.transitSchedule = transitSchedule;
        this.accessEgressRouteCache = accessEgressRouteCache;

        SBBIntermodalConfigGroup intermodalConfigGroup = ConfigUtils.addOrGetModule(config, SBBIntermodalConfigGroup.class);
        this.intermodalModeParams = intermodalConfigGroup.getModeParameterSets().stream().collect(Collectors.toMap(set -> set.getMode(), set -> set));
        SwissRailRaptorConfigGroup srrConfig = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class);
        this.routingModules = new HashMap<>();
        if (srrConfig.isUseIntermodalAccessEgress()) {
            for (IntermodalAccessEgressParameterSet params : srrConfig.getIntermodalAccessEgressParameterSets()) {
                String mode = params.getMode();
                this.routingModules.put(mode, routingModuleProviders.get(mode).get());

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
        switch (srrCfg.getIntermodalAccessEgressModeSelection()) {
            case CalcLeastCostModePerStop:
                for (IntermodalAccessEgressParameterSet parameterSet : srrCfg.getIntermodalAccessEgressParameterSets()) {
                    addInitialStopsForParamSet(facility, person, departureTime, direction, parameters, data, x, y, initialStops, parameterSet);
                }
                break;
            case RandomSelectOneModePerRoutingRequestAndDirection:
                int counter = 0;
                do {
                    int rndSelector = random.nextInt(srrCfg.getIntermodalAccessEgressParameterSets().size());
                    log.debug("findIntermodalStops: rndSelector=" + rndSelector);
                    addInitialStopsForParamSet(facility, person, departureTime, direction, parameters, data, x, y,
                            initialStops, srrCfg.getIntermodalAccessEgressParameterSets().get(rndSelector));
                    counter++;
                    // try again if no initial stop was found for the parameterset. Avoid infinite loop by limiting number of tries.
                } while (initialStops.isEmpty() && counter < 2 * srrCfg.getIntermodalAccessEgressParameterSets().size());
                break;
            default:
                throw new RuntimeException(srrCfg.getIntermodalAccessEgressModeSelection() + " : not implemented!");
        }

        return initialStops;
    }

    private void addInitialStopsForParamSet(Facility facility, Person person, double departureTime, Direction direction, RaptorParameters parameters, SwissRailRaptorData data, double x, double y, List<InitialStop> initialStops, IntermodalAccessEgressParameterSet paramset) {
        double radius = paramset.getMaxRadius();
        String mode = paramset.getMode();
        SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet params = this.intermodalModeParams.get(mode);

        boolean useMinimalTransferTimes = false;
        if (this.doUseMinimalTransferTimes(mode)) {
            useMinimalTransferTimes = true;
        }
        String overrideMode = null;
        if (mode.equals(SBBModes.WALK) || mode.equals(SBBModes.PT_FALLBACK_MODE)) {
            overrideMode = SBBModes.NON_NETWORK_WALK;
        }
        String linkIdAttribute = paramset.getLinkIdAttribute();
        String personFilterAttribute = paramset.getPersonFilterAttribute();
        String personFilterValue = paramset.getPersonFilterValue();
        String stopFilterAttribute = paramset.getStopFilterAttribute();
        String stopFilterValue = paramset.getStopFilterValue();

        boolean personMatches = true;
        if (personFilterAttribute != null) {
            Object attr = person.getAttributes().getAttribute(personFilterAttribute);
            String attrValue = attr == null ? null : attr.toString();
            personMatches = personFilterValue.equals(attrValue);
        }

        if (personMatches) {
            Collection<TransitStopFacility> stopFacilities = data.stopsQT.getDisk(x, y, radius);
            for (TransitStopFacility stop : stopFacilities) {
                boolean filterMatches = true;
                if (stopFilterAttribute != null) {
                    Object attr = stop.getAttributes().getAttribute(stopFilterAttribute);
                    String attrValue = attr == null ? null : attr.toString();
                    filterMatches = stopFilterValue.equals(attrValue);
                }
                if (filterMatches) {
                    Facility stopFacility = stop;
                    if (linkIdAttribute != null) {
                        Object attr = stop.getAttributes().getAttribute(linkIdAttribute);
                        if (attr != null) {
                            stopFacility = new ChangedLinkFacility(stop, Id.create(attr.toString(), Link.class));
                        }
                    }

                    List<? extends PlanElement> routeParts;
                    RoutingModule module = this.routingModules.get(mode);
                    if (direction == Direction.ACCESS) {
                        if (params != null && params.isRoutedOnNetwork() && (!params.isSimulatedOnNetwork())) {
                            routeParts = getCachedTravelTime(stopFacility, facility, departureTime, person, mode, module, true);
                        } else {
                            routeParts = module.calcRoute(facility, stopFacility, departureTime, person);
                        }

                    } else { // it's Egress
                        // We don't know the departure time for the egress trip, so just use the original departureTime,
                        // although it is wrong and might result in a wrong traveltime and thus wrong route.
                        if (params != null && params.isRoutedOnNetwork() && (!params.isSimulatedOnNetwork())) {
                            routeParts = getCachedTravelTime(stopFacility, facility, departureTime, person, mode, module, false);
                        } else {
                            routeParts = module.calcRoute(stopFacility, facility, departureTime, person);
                        }
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
                            Leg transferLeg = PopulationUtils.createLeg(SBBModes.NON_NETWORK_WALK);
                            Route transferRoute = RouteUtils.createGenericRouteImpl(stopFacility.getLinkId(), stop.getLinkId());
                            double transferTime = 0.0;
                            if (useMinimalTransferTimes) {
                                transferTime = this.getMinimalTransferTime(stop);
                            }
                            transferRoute.setTravelTime(transferTime);
                            transferRoute.setDistance(0);
                            transferLeg.setRoute(transferRoute);
                            transferLeg.setTravelTime(transferTime);

                            List<PlanElement> tmp = new ArrayList<>(routeParts.size() + 1);
                            tmp.addAll(routeParts);
                            tmp.add(transferLeg);
                            routeParts = tmp;
                        } else {
                            Leg transferLeg = PopulationUtils.createLeg(SBBModes.NON_NETWORK_WALK);
                            Route transferRoute = RouteUtils.createGenericRouteImpl(stop.getLinkId(), stopFacility.getLinkId());
                            double transferTime = 0.0;
                            if (useMinimalTransferTimes) {
                                transferTime = this.getMinimalTransferTime(stop);
                            }
                            transferRoute.setTravelTime(transferTime);
                            transferRoute.setDistance(0);
                            transferLeg.setRoute(transferRoute);
                            transferLeg.setTravelTime(transferTime);

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

    }

    private List<? extends PlanElement> getCachedTravelTime(Facility stopFacility, Facility actFacility, double departureTime, Person person, String mode, RoutingModule module, boolean backwards) {
        AccessEgressRouteCache.RouteCharacteristics characteristics = this.accessEgressRouteCache.getCachedRouteCharacteristics(mode, stopFacility.getLinkId(), actFacility.getLinkId());

        Id<Link> startLink = backwards ? actFacility.getLinkId() : stopFacility.getLinkId();
        Id<Link> endLink = backwards ? stopFacility.getLinkId() : actFacility.getLinkId();
        List<PlanElement> travel = new ArrayList<>();
        double accessTime = backwards ? characteristics.getEgressTime() : characteristics.getAccessTime();
        double egressTime = backwards ? characteristics.getAccessTime() : characteristics.getEgressTime();
        if (!Double.isNaN(accessTime)) {
            Leg leg = createAccessEgressLeg(accessTime, startLink);
            travel.add(leg);
            Activity stage = createStageAct(startLink);
            travel.add(stage);
        }
        Leg leg = PopulationUtils.createLeg(mode);
        Route route = RouteUtils.createGenericRouteImpl(startLink, endLink);
        route.setTravelTime(characteristics.getTravelTime());
        route.setDistance(characteristics.getDistance());
        leg.setTravelTime(characteristics.getTravelTime());
        leg.setRoute(route);
        travel.add(leg);
        if (!Double.isNaN(egressTime)) {
            Activity stage = createStageAct(startLink);
            travel.add(stage);
            Leg leg3 = createAccessEgressLeg(egressTime, endLink);
            travel.add(leg3);
        }
        return travel;


    }

    private Activity createStageAct(Id<Link> linkId) {
        Activity activity = PopulationUtils.createActivityFromLinkId("pt interaction", linkId);
        activity.setMaximumDuration(0);
        return activity;
    }

    private Leg createAccessEgressLeg(double traveltime, Id<Link> link) {
        Leg leg = PopulationUtils.createLeg(SBBModes.NON_NETWORK_WALK);
        Route route = RouteUtils.createGenericRouteImpl(link, link);
        route.setTravelTime(traveltime);
        route.setDistance(0.0);
        leg.setTravelTime(traveltime);
        leg.setRoute(route);
        return leg;
    }


    private boolean doUseMinimalTransferTimes(String mode) {
        for (SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet modeParams : this.intermodalModeParams.values()) {
            if (mode.equals(modeParams.getMode())) {
                return modeParams.doUseMinimalTransferTimes();
            }
        }
        return false;
    }

    private double getMinimalTransferTime(TransitStopFacility stop) {
        MinimalTransferTimes mtt = this.transitSchedule.getMinimalTransferTimes();
        double transferTime = mtt.get(stop.getId(), stop.getId());
        if (Double.isNaN(transferTime)) {
            // return a default value of 30 seconds
            return 30.0;
        } else {
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
