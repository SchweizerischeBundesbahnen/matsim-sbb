package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.config.SBBIntermodalConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.population.routes.RouteUtils;
import org.matsim.core.router.DijkstraFactory;
import org.matsim.core.router.SingleModeNetworksCache;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.util.LeastCostPathCalculator;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.TransitSchedule;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author jbischoff / SBB
 */
public class AccessEgressRouteCache {

    private final static Logger LOGGER = Logger.getLogger(AccessEgressRouteCache.class);
    public static final double FREESPEED_FACTOR = 0.8;
    private final Map<String, SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet> intermodalModeParams = new HashMap<>();
    private final Map<String, SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet> raptorIntermodalModeParams;
    private final TransitSchedule transitSchedule;
    private final Zones zonesCollection;
    private final Scenario scenario;
    private Map<String, Map<Id<Link>, Integer>> accessTimes = new HashMap<>();
    private Map<String, Map<Id<Link>, Map<Id<Link>, int[]>>> travelTimesDistances = new HashMap<>();
    private Map<String, LeastCostPathCalculator> leastCostPathCalculators = new HashMap<>();
    private SingleModeNetworksCache singleModeNetworksCache;


    @Inject
    public AccessEgressRouteCache(ZonesCollection allZones, SingleModeNetworksCache singleModeNetworksCache, Config config, Scenario scenario) {
        LOGGER.info("Access Egress Route cache.");
        this.scenario = scenario;
        this.transitSchedule = scenario.getTransitSchedule();
        this.singleModeNetworksCache = singleModeNetworksCache;
        SBBIntermodalConfigGroup intermodalConfigGroup = ConfigUtils.addOrGetModule(config, SBBIntermodalConfigGroup.class);
        SwissRailRaptorConfigGroup railRaptorConfigGroup = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class);
        raptorIntermodalModeParams = railRaptorConfigGroup.getIntermodalAccessEgressParameterSets().stream().collect(Collectors.toMap(m -> m.getMode(), m -> m));
        this.zonesCollection = allZones.getZones(intermodalConfigGroup.getZonesId());
        for (SBBIntermodalConfigGroup.SBBIntermodalModeParameterSet paramset : intermodalConfigGroup.getModeParameterSets()) {
            if (paramset.isRoutedOnNetwork() && !paramset.isSimulatedOnNetwork()) {
                LOGGER.info("Building Traveltime cache for feeder mode " + paramset.getMode() + "....");
                Gbl.printMemoryUsage();
                this.intermodalModeParams.put(paramset.getMode(), paramset);
                SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet raptorParams = raptorIntermodalModeParams.get(paramset.getMode());
                String stopFilterAttribute = raptorParams.getStopFilterAttribute();
                String stopFilterValue = raptorParams.getStopFilterValue();
                String linkIdAttribute = raptorParams.getLinkIdAttribute();
                Set<Id<Link>> stopLinkIds = transitSchedule.getFacilities().values().stream()
                        .filter(f -> String.valueOf(f.getAttributes().getAttribute(stopFilterAttribute)).equals(stopFilterValue))
                        .map(f -> Id.createLinkId(String.valueOf(f.getAttributes().getAttribute(linkIdAttribute))))
                        .collect(Collectors.toSet());
                LOGGER.info("Found " + stopLinkIds.size() + " stops with intermodal access option for this mode.");
                Network network = getRoutingNetwork(paramset.getMode());
                final FreeSpeedTravelTime freeSpeedTravelTime = new FreeSpeedTravelTime();
                final FreespeedTravelTimeAndDisutility travelTimeAndDisutility = new FreespeedTravelTimeAndDisutility(config.planCalcScore());
                this.leastCostPathCalculators.put(paramset.getMode(), new DijkstraFactory(false).createPathCalculator(network, travelTimeAndDisutility, freeSpeedTravelTime));
                Map<Id<Link>, Integer> modeAccessTimes = calcModeAccessTimes(stopLinkIds, paramset.getAccessTimeZoneId(), network);
                accessTimes.put(paramset.getMode(), modeAccessTimes);
                final double maxRadius = raptorParams.getMaxRadius();
                Map<Id<Link>, Map<Id<Node>, SBBLeastCostPathTree.NodeData>> travelTimes = stopLinkIds.parallelStream()
                        .collect(Collectors.toMap(l -> l, l -> {
                            SBBLeastCostPathTree leastCostPathTree = new SBBLeastCostPathTree(freeSpeedTravelTime, travelTimeAndDisutility);
                            Node fromNode = network.getLinks().get(l).getToNode();
                            return leastCostPathTree.calculate(network, fromNode, 0, new SBBLeastCostPathTree.TravelDistanceStopCriterion(maxRadius * 1.5));

                        }));
                Map<Id<Link>, Map<Id<Link>, int[]>> travelTimeLinks = new HashMap<>();
                for (Map.Entry<Id<Link>, Map<Id<Node>, SBBLeastCostPathTree.NodeData>> entry : travelTimes.entrySet()) {
                    Map<Id<Link>, int[]> travelTimesToLink = new HashMap<>();
                    for (Map.Entry<Id<Node>, SBBLeastCostPathTree.NodeData> nodeDataEntry : entry.getValue().entrySet()) {
                        Node node = network.getNodes().get(nodeDataEntry.getKey());
                        int travelTime = (int) Math.round(nodeDataEntry.getValue().getTime());
                        int travelDistance = (int) Math.round(nodeDataEntry.getValue().getDistance());
                        int egressTime = getAccessTime(paramset.getAccessTimeZoneId(), node.getCoord());
                        int[] data = new int[]{travelTime, travelDistance, egressTime};
                        for (Id<Link> inlink : node.getInLinks().keySet()) {
                            travelTimesToLink.put(inlink, data);
                        }
                    }

                    travelTimeLinks.put(entry.getKey(), travelTimesToLink);
                }
                this.travelTimesDistances.put(paramset.getMode(), travelTimeLinks);
                Gbl.printMemoryUsage();
                LOGGER.info("...done.");

            }
        }

    }

    private Map<Id<Link>, Integer> calcModeAccessTimes(Set<Id<Link>> stopLinkIds, String accessTimeZoneId, Network network) {
        Map<Id<Link>, Integer> accessTimes = new HashMap<>();
        if (accessTimeZoneId == null) {
            stopLinkIds.stream().forEach(i -> accessTimes.put(i, 0));
        } else {
            stopLinkIds.parallelStream().forEach(l -> {
                Coord coord = network.getLinks().get(l).getCoord();
                Integer accessTime = getAccessTime(accessTimeZoneId, coord);
                accessTimes.put(l, accessTime);
            });
        }

        return accessTimes;
    }

    private int getAccessTime(String accessTimeZoneId, Coord coord) {
        if (accessTimeZoneId == null) {
            return 0;
        }
        Zone zone = zonesCollection.findZone(coord);
        if (zone != null) {
            Integer at = (Integer) zone.getAttribute(accessTimeZoneId);
            return at != null ? at : 0;
        } else return 0;
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

    public RouteCharacteristics getCachedRouteCharacteristics(String mode, Id<Link> stopFacilityLinkId, Id<Link> endLinkFacility) {
        Map<Id<Link>, Map<Id<Link>, int[]>> modalStats = this.travelTimesDistances.get(mode);
        Map<Id<Link>, int[]> facStats = modalStats.get(stopFacilityLinkId);
        int[] value = facStats.get(endLinkFacility);
        int accessTime = accessTimes.get(mode).get(stopFacilityLinkId);
        if (facStats == null) {
            throw new RuntimeException("Stop at linkId " + stopFacilityLinkId + " is not a listed stop for intermodal access egress.");
        }
        if (value == null) {
            //we are (slightly) outside pre-cached radius
            final Network routingNetwork = getRoutingNetwork(mode);
            Node fromNode = routingNetwork.getLinks().get(stopFacilityLinkId).getToNode();
            Node toNode = routingNetwork.getLinks().get(endLinkFacility).getToNode();
            int distance;
            int traveltime;
            try {
                LeastCostPathCalculator.Path path = this.leastCostPathCalculators.get(mode).calcLeastCostPath(fromNode, toNode, 0, null, null);
                distance = (int) RouteUtils.calcDistance(path);
                traveltime = (int) path.travelTime;
            } catch (NullPointerException e) {
                LOGGER.warn("Estimating access route: could not find route between nodes " + fromNode + " and " + toNode);
                distance = (int) (CoordUtils.calcEuclideanDistance(fromNode.getCoord(), toNode.getCoord()) * 1.5);
                traveltime = (int) (distance / 8.0);
            }
            int egressTime = getAccessTime(this.intermodalModeParams.get(mode).getAccessTimeZoneId(), toNode.getCoord());
            synchronized (facStats) {
                value = new int[]{distance, traveltime, egressTime};
                facStats.put(endLinkFacility, value);
            }
        }
        return new RouteCharacteristics(value[0], accessTime, value[2], value[1] * FREESPEED_FACTOR);
    }


    static class RouteCharacteristics {
        private final double distance;
        private final double accessTime;
        private final double egressTime;
        private final double travelTime;

        public RouteCharacteristics(double distance, double accessTime, double egressTime, double travelTime) {
            this.distance = distance;
            this.accessTime = accessTime;
            this.egressTime = egressTime;
            this.travelTime = travelTime;
        }

        public double getDistance() {
            return distance;
        }

        public double getAccessTime() {
            return accessTime;
        }

        public double getEgressTime() {
            return egressTime;
        }

        public double getTravelTime() {
            return travelTime;
        }

    }
}
