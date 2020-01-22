package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.config.SBBAccessTimeConfigGroup;
import ch.sbb.matsim.config.SBBIntermodalConfigGroup;
import ch.sbb.matsim.config.SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.routing.access.AccessEgressRouting;
import ch.sbb.matsim.routing.pt.raptor.SBBLeastCostPathTree.NodeData;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.api.core.v01.population.Route;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.SingleModeNetworksCache;
import org.matsim.core.router.costcalculators.TravelDisutilityFactory;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.misc.Time;
import org.matsim.facilities.Facility;
import org.matsim.pt.transitSchedule.api.MinimalTransferTimes;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import javax.inject.Inject;
import javax.inject.Provider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author mrieser / Simunto GmbH
 */
public class SBBIntermodalRaptorStopFinder implements RaptorStopFinder {

    private final static Logger log = Logger.getLogger(SBBIntermodalRaptorStopFinder.class);

    private final Scenario scenario;
    private final TransitSchedule transitSchedule;
    private final RaptorIntermodalAccessEgress intermodalAE;
    private final Map<String, SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet> intermodalModeParams = new HashMap<>();
    private final Map<String, RoutingModule> routingModules;
    private final Map<String, TravelTime> travelTimes;
    private final Map<String, TravelDisutilityFactory> travelDisutilityFactories;
    private final Set<String> networkModes;
    private final SingleModeNetworksCache singleModeNetworksCache;
    private final Map<String, AccessEgressRouting> accessEgressRouting = new HashMap<>();

    @Inject
    public SBBIntermodalRaptorStopFinder(Scenario scenario, RaptorIntermodalAccessEgress intermodalAE,
                                         Map<String, Provider<RoutingModule>> routingModuleProviders,
                                         Map<String, TravelTime> travelTimes,
                                         Map<String, TravelDisutilityFactory> travelDisutilityFactories,
                                         SingleModeNetworksCache singleModeNetworksCache, ZonesCollection allZones) {
        this.intermodalAE = intermodalAE;
        this.scenario = scenario;
        this.transitSchedule = scenario.getTransitSchedule();
        Config config = scenario.getConfig();

        SBBIntermodalConfigGroup intermodalConfigGroup = ConfigUtils.addOrGetModule(config, SBBIntermodalConfigGroup.class);
        for (SBBIntermodalModeParameterSet paramset : intermodalConfigGroup.getModeParameterSets()) {
            this.intermodalModeParams.put(paramset.getMode(), paramset);
        }

        SBBAccessTimeConfigGroup accessTimeConfigGroup = ConfigUtils.addOrGetModule(config, SBBAccessTimeConfigGroup.GROUP_NAME, SBBAccessTimeConfigGroup.class);
        boolean useAccessEgress = config.plansCalcRoute().isInsertingAccessEgressWalk() || accessTimeConfigGroup.getInsertingAccessEgressWalk();

        SwissRailRaptorConfigGroup srrConfig = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class);
        this.routingModules = new HashMap<>();
        this.networkModes = new HashSet<>();
        if (srrConfig.isUseIntermodalAccessEgress()) {
            for (IntermodalAccessEgressParameterSet srrParams : srrConfig.getIntermodalAccessEgressParameterSets()) {
                String mode = srrParams.getMode();
                SBBIntermodalModeParameterSet params = this.intermodalModeParams.get(mode);
                if (params != null && params.isRoutedOnNetwork()) {
                    this.networkModes.add(mode);
                    if (useAccessEgress) {
                        Zones zones = allZones.getZones(accessTimeConfigGroup.getZonesId());
                        String replacementMode = params.getAccessTimeZoneId();
                        if (replacementMode == null) {
                            replacementMode = mode;
                        } else {
                            replacementMode = replacementMode.substring(3); // cut off "ACC" in the beginning;
                        }
                        this.accessEgressRouting.put(mode, new AccessEgressRouting(zones, scenario.getPopulation().getFactory(), replacementMode, scenario.getNetwork()));
                    }
                } else {
                    this.routingModules.put(mode, routingModuleProviders.get(mode).get());
                }
            }
        }
        this.travelTimes = travelTimes;
        this.travelDisutilityFactories = travelDisutilityFactories;
        this.singleModeNetworksCache = singleModeNetworksCache;
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
                    int rndSelector = (int) (MatsimRandom.getRandom().nextDouble() * srrCfg.getIntermodalAccessEgressParameterSets().size());
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

        if (personMatches(person, paramset)) {
            Collection<TransitStopFacility> stopFacilities = data.stopsQT.getDisk(x, y, radius);
            boolean isNetworkMode = this.networkModes.contains(mode);
            if (isNetworkMode) {
                calculateNetworkRoutesToStops(stopFacilities, paramset, parameters, person, direction, mode, facility, departureTime, initialStops);
            } else {
                calculateIndividualRoutesToStops(stopFacilities, paramset, parameters, person, direction, mode, facility, departureTime, initialStops);
            }
        }

    }

    private void calculateNetworkRoutesToStops(Collection<TransitStopFacility> stopFacilities, IntermodalAccessEgressParameterSet paramset, RaptorParameters parameters,
                                               Person person, Direction direction, String mode, Facility facility, double departureTime, List<InitialStop> initialStops) {
        String linkIdAttribute = paramset.getLinkIdAttribute();
        boolean useMinimalTransferTimes = doUseMinimalTransferTimes(mode);

        TravelTime tt = this.travelTimes.get(mode);
        TravelDisutility tc = this.travelDisutilityFactories.get(mode).createTravelDisutility(tt);
        SBBLeastCostPathTree tree = new SBBLeastCostPathTree(tt, tc);

        Network routingNetwork = getRoutingNetwork(mode);

        Link originLink = routingNetwork.getLinks().get(facility.getLinkId());
        if (originLink == null) {
            originLink = NetworkUtils.getNearestLinkExactly(routingNetwork, facility.getCoord());
        }

        List<Tuple<Link, TransitStopFacility>> stopsAndLinks = new ArrayList<>();

        for (TransitStopFacility stop : stopFacilities) {
            if (stopMatches(stop, paramset)) {
                Id<Link> linkId = getStopLinkId(stop, linkIdAttribute);
                Link link = routingNetwork.getLinks().get(linkId);
                if (link == null) {
                    throw new RuntimeException("Link " + linkId + " for stop " + stop + " could not be found in routing-network for mode " + mode);
                }
                stopsAndLinks.add(new Tuple<>(link, stop));
            }
        }

        Set<Node> nodesToReach = new HashSet<>();

        SBBLeastCostPathTree.StopCriterion stopCriterion = (node, data, depTime) -> {
            nodesToReach.remove(node);
            return nodesToReach.isEmpty();
        };

        if (direction == Direction.ACCESS) {
            Node origin = originLink.getToNode();
            for (Tuple<Link, TransitStopFacility> t : stopsAndLinks) {
                nodesToReach.add(t.getFirst().getFromNode());
            }
            Map<Id<Node>, NodeData> data = tree.calculate(routingNetwork, origin, departureTime, stopCriterion);
            for (Tuple<Link, TransitStopFacility> t : stopsAndLinks) {
                Link link = t.getFirst();
                TransitStopFacility stop = t.getSecond();

                NodeData destination = data.get(link.getFromNode().getId());
                if (destination == null) {
                    continue; // stop could not be reached
                }

                Leg accessLeg = PopulationUtils.createLeg(mode);
                List<Id<Link>> routeLinkIds = new ArrayList<>();
                routeLinkIds.add(link.getId());
                NodeData d = destination;
                while (d.getLink() != null) {
                    Link l = d.getLink();
                    routeLinkIds.add(l.getId());
                    d = data.get(l.getFromNode().getId());
                }
                routeLinkIds.add(originLink.getId());
                Collections.reverse(routeLinkIds);
                Route route = RouteUtils.createNetworkRoute(routeLinkIds, routingNetwork);
                route.setTravelTime(destination.getTime() - departureTime + tt.getLinkTravelTime(link, destination.getTime(), person, null));
                route.setDistance(destination.getDistance() + link.getLength());
                accessLeg.setRoute(route);
                accessLeg.setTravelTime(route.getTravelTime());

                Leg transferLeg = createTransferLeg(link.getId(), stop.getLinkId(), useMinimalTransferTimes, stop);

                List<PlanElement> routeParts = createRouteParts(mode, direction, facility, stop, accessLeg, originLink, link, transferLeg, person);

                RaptorIntermodalAccessEgress.RIntermodalAccessEgress accessEgress = this.intermodalAE.calcIntermodalAccessEgress(routeParts, parameters, person);
                InitialStop iStop = new InitialStop(stop, accessEgress.disutility, accessEgress.travelTime, accessEgress.routeParts);
                initialStops.add(iStop);
            }
        }
        if (direction == Direction.EGRESS) {
            Node origin = originLink.getFromNode();
            for (Tuple<Link, TransitStopFacility> t : stopsAndLinks) {
                nodesToReach.add(t.getFirst().getToNode());
            }
            double egressArrivalTime = departureTime + 3600; // we don't know the actual arrival time...
            Map<Id<Node>, NodeData> data = tree.calculateBackwards(routingNetwork, origin, egressArrivalTime, stopCriterion);
            for (Tuple<Link, TransitStopFacility> t : stopsAndLinks) {
                Link link = t.getFirst();
                TransitStopFacility stop = t.getSecond();

                NodeData destination = data.get(link.getToNode().getId());

                Leg egressLeg = PopulationUtils.createLeg(mode);
                List<Id<Link>> routeLinkIds = new ArrayList<>();
                routeLinkIds.add(link.getId());
                NodeData d = destination;
                while (d.getLink() != null) {
                    Link l = d.getLink();
                    routeLinkIds.add(l.getId());
                    d = data.get(l.getToNode().getId());
                }
                routeLinkIds.add(originLink.getId());
                Route route = RouteUtils.createNetworkRoute(routeLinkIds, routingNetwork);
                route.setTravelTime(egressArrivalTime - destination.getTime() + tt.getLinkTravelTime(originLink, egressArrivalTime, person, null));
                route.setDistance(destination.getDistance() + originLink.getLength());
                egressLeg.setRoute(route);
                egressLeg.setTravelTime(route.getTravelTime());

                Leg transferLeg = createTransferLeg(stop.getLinkId(), link.getId(), useMinimalTransferTimes, stop);

                List<PlanElement> routeParts = createRouteParts(mode, direction, facility, stop, egressLeg, link, originLink, transferLeg, person);

                RaptorIntermodalAccessEgress.RIntermodalAccessEgress accessEgress = this.intermodalAE.calcIntermodalAccessEgress(routeParts, parameters, person);
                InitialStop iStop = new InitialStop(stop, accessEgress.disutility, accessEgress.travelTime, accessEgress.routeParts);
                initialStops.add(iStop);
            }
        }
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
                    if (module == null) {
                        throw new RuntimeException("Could not find routing module for mode " + mode);
                    }
                    routeParts = module.calcRoute(facility, stopFacility, departureTime, person);
                } else { // it's Egress
                    // We don't know the departure time for the egress trip, so just use the original departureTime,
                    // although it is wrong and might result in a wrong traveltime and thus wrong route.
                    RoutingModule module = this.routingModules.get(mode);
                    if (module == null) {
                        throw new RuntimeException("Could not find routing module for mode " + mode);
                    }
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
        SBBIntermodalModeParameterSet modeParams = this.intermodalModeParams.get(mode);
        if (modeParams != null) {
            return modeParams.doUseMinimalTransferTimes();
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

    private Id<Link> getStopLinkId(TransitStopFacility stop, String linkIdAttribute) {
        if (linkIdAttribute == null) {
            return stop.getLinkId();
        } else {
            Object attr = stop.getAttributes().getAttribute(linkIdAttribute);
            if (attr == null) {
                return stop.getLinkId();
            } else {
                return Id.create(attr.toString(), Link.class);
            }
        }
    }

    private Network getRoutingNetwork(String mode) {
        Map<String, Network> cache = this.singleModeNetworksCache.getSingleModeNetworksCache();
        Network filteredNetwork = cache.get(mode);
        if (filteredNetwork == null) {
            // Ensure this is not performed concurrently by multiple threads!
            synchronized (cache) {
                filteredNetwork = cache.get(mode);
                if (filteredNetwork == null) {
                    TransportModeNetworkFilter filter = new TransportModeNetworkFilter(this.scenario.getNetwork());
                    Set<String> modes = new HashSet<>();
                    modes.add(mode);
                    filteredNetwork = NetworkUtils.createNetwork();
                    filter.filter(filteredNetwork, modes);
                    cache.put(mode, filteredNetwork);
                }
            }
        }
        return filteredNetwork;
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

    private List<PlanElement> createRouteParts(String mode, Direction direction, Facility facility, TransitStopFacility stop, Leg feederLeg, Link feederStartLink, Link feederEndLink, Leg transferLeg, Person person) {
        AccessEgressRouting aeRouting = this.accessEgressRouting.get(mode);
        List<PlanElement> routeParts = new ArrayList<>();
        if (direction == Direction.ACCESS) {
            if (aeRouting != null) {
                aeRouting.addAccess(facility, feederStartLink, feederLeg.getDepartureTime(), routeParts, person);
            }
            routeParts.add(feederLeg);
            if (aeRouting != null) {
                aeRouting.addEgress(stop, feederEndLink, transferLeg.getDepartureTime(), routeParts, person);
            }
            routeParts.add(transferLeg);
        } else { // --> EGRESS
            routeParts.add(transferLeg);
            if (aeRouting != null) {
                aeRouting.addAccess(stop, feederStartLink, feederLeg.getDepartureTime(), routeParts, person);
            }
            routeParts.add(feederLeg);
            if (aeRouting != null) {
                aeRouting.addEgress(facility, feederEndLink, feederLeg.getDepartureTime() + feederLeg.getTravelTime(), routeParts, person);
            }
        }
        return routeParts;
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
