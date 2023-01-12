package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.RunSBB;
import ch.sbb.matsim.config.SBBIntermodalConfiggroup;
import ch.sbb.matsim.config.SBBIntermodalModeParameterSet;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet;
import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesCollection;
import ch.sbb.matsim.zones.ZonesModule;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.Gbl;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.router.DefaultRoutingRequest;
import org.matsim.core.router.RoutingModule;
import org.matsim.core.router.SingleModeNetworksCache;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.router.costcalculators.FreespeedTravelTimeAndDisutility;
import org.matsim.core.router.speedy.LeastCostPathTree;
import org.matsim.core.router.speedy.SpeedyGraph;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.OptionalTime;
import org.matsim.facilities.Facility;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleUtils;

import javax.inject.Inject;
import java.io.BufferedWriter;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * @author jbischoff / SBB
 */
public class AccessEgressRouteCache {

	public static final double FREESPEED_TRAVELTIME_FACTOR = 1.25;
	private final static Logger LOGGER = LogManager.getLogger(AccessEgressRouteCache.class);
	private final static Vehicle VEHICLE = VehicleUtils.getFactory().createVehicle(Id.create("theVehicle", Vehicle.class), VehicleUtils.getDefaultVehicleType());
	private final static Person PERSON = PopulationUtils.getFactory().createPerson(Id.create("thePerson", Person.class));
	private final Map<String, SBBIntermodalModeParameterSet> intermodalModeParams = new HashMap<>();
	private final Zones zonesCollection;
	private final Scenario scenario;
	private final Map<String, Map<Id<Link>, Integer>> accessTimes = new HashMap<>();
	private final Map<String, Map<Id<Link>, Map<Id<Link>, int[]>>> travelTimesDistances = new HashMap<>();
	private final SingleModeNetworksCache singleModeNetworksCache;

	@Inject
	public AccessEgressRouteCache(ZonesCollection allZones, SingleModeNetworksCache singleModeNetworksCache, Config config, Scenario scenario) {
		LOGGER.info("Access Egress Route cache.");
		this.scenario = scenario;
		TransitSchedule transitSchedule = scenario.getTransitSchedule();
		this.singleModeNetworksCache = singleModeNetworksCache;
		SBBIntermodalConfiggroup intermodalConfigGroup = ConfigUtils.addOrGetModule(config, SBBIntermodalConfiggroup.class);
		SwissRailRaptorConfigGroup railRaptorConfigGroup = ConfigUtils.addOrGetModule(config, SwissRailRaptorConfigGroup.class);
		Map<String, IntermodalAccessEgressParameterSet> raptorIntermodalModeParams = railRaptorConfigGroup.getIntermodalAccessEgressParameterSets().stream()
				.collect(Collectors.toMap(IntermodalAccessEgressParameterSet::getMode, m -> m, (m, n) -> m));
		this.zonesCollection = allZones.getZones(intermodalConfigGroup.getZonesId());
		for (SBBIntermodalModeParameterSet paramset : intermodalConfigGroup.getModeParameterSets()) {
			if (paramset.isRoutedOnNetwork() && !paramset.isSimulatedOnNetwork()) {
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
				Network network = getRoutingNetwork(paramset.getMode(), config);
                Map<Id<Link>, Integer> modeAccessTimes = calcModeAccessTimes(stopLinkIds, paramset.getAccessTimeZoneId(), network);
				accessTimes.put(paramset.getMode(), modeAccessTimes);

				if (paramset.getIntermodalAccessCacheFileString() == null) {
					buildRoutingCacheForMode(config, paramset, raptorParams, stopLinkIds, network);
				} else {
					readCachedTraveltimesFromFile(paramset.getIntermodalAccessCacheFile(config.getContext()), paramset.getMode());
				}

			}
		}

	}

	public static void main(String[] args) {
		Config config = RunSBB.buildConfig(args[0]);
		String outputPath = args[1];
		SingleModeNetworksCache singleModeNetworksCache = new SingleModeNetworksCache();
		Scenario scenario = ScenarioUtils.loadScenario(config);
		RunSBB.addSBBDefaultScenarioModules(scenario);
		AccessEgressRouteCache cache = new AccessEgressRouteCache((ZonesCollection) scenario.getScenarioElement(ZonesModule.SBB_ZONES), singleModeNetworksCache, config, scenario);
		cache.writeCachedTraveltimes(outputPath);
	}

	private void readCachedTraveltimesFromFile(URL intermodalAccessCacheFile, String mode) {
		var modemap = this.travelTimesDistances.computeIfAbsent(mode, a -> new HashMap<>());
		LOGGER.info("Reading intermodal cache for mode " + mode + "from File: " + intermodalAccessCacheFile.toString());
		final String stop = "stop";
		final String link = "link";
		final String v0 = "0";
		final String v1 = "1";
		final String s = "2";
		try (CSVReader csvReader = new CSVReader(new String[]{stop, link, v0, v1, s}, intermodalAccessCacheFile, ";")) {
			var line = csvReader.readLine();
			while (line != null) {
				if (line.get(stop) == null) {
					break;
				}
				var stationMap = modemap.computeIfAbsent(Id.createLinkId(line.get(stop)), a -> new HashMap<>());
				int[] cachedtimes = {Integer.parseInt(line.get(v0)), Integer.parseInt(line.get(v1)), Integer.parseInt(line.get(s))};
				var toLink = Id.createLinkId(line.get(link));
				stationMap.put(toLink, cachedtimes);
				line = csvReader.readLine();

			}
			LOGGER.info("done");

		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public void buildRoutingCacheForMode(Config config, SBBIntermodalModeParameterSet paramset, IntermodalAccessEgressParameterSet raptorParams, Set<Id<Link>> stopLinkIds, Network network) {
        SpeedyGraph graph = new SpeedyGraph(network);
        LOGGER.info("Building Traveltime cache for feeder mode " + paramset.getMode() + "....");
        final double maxRadius = raptorParams.getMaxRadius();
        final FreeSpeedTravelTime freeSpeedTravelTime = new FreeSpeedTravelTime();
        final FreespeedTravelTimeAndDisutility travelTimeAndDisutility = new FreespeedTravelTimeAndDisutility(config.planCalcScore());
        Map<Id<Link>, LeastCostPathTree> travelTimes = stopLinkIds.parallelStream()
                .collect(Collectors.toMap(l -> l, l -> {
                    LeastCostPathTree leastCostPathTree = new LeastCostPathTree(graph, freeSpeedTravelTime, travelTimeAndDisutility);
                    Node fromNode = network.getLinks().get(l).getToNode();
                    leastCostPathTree.calculate(fromNode.getId().index(), 0, PERSON, VEHICLE, new LeastCostPathTree.TravelDistanceStopCriterion(maxRadius * 1.5));
                    return leastCostPathTree;

				}));
		Map<Id<Link>, Map<Id<Link>, int[]>> travelTimeLinks = new HashMap<>();
		for (Map.Entry<Id<Link>, LeastCostPathTree> entry : travelTimes.entrySet()) {
			Map<Id<Link>, int[]> travelTimesToLink = new ConcurrentHashMap<>();
			LeastCostPathTree tree = entry.getValue();
			for (Node node : network.getNodes().values()) {
				int nodeIndex = node.getId().index();
				OptionalTime arrivalTime = tree.getTime(nodeIndex);
				if (arrivalTime.isUndefined()) {
					continue; // node was not reached, skip to next node.
				}
				int travelTime = (int) Math.round(arrivalTime.seconds());
                int travelDistance = (int) Math.round(tree.getDistance(nodeIndex));
                int egressTime = getAccessTime(paramset.getAccessTimeZoneId(), node.getCoord());
                int[] data = new int[]{travelDistance, travelTime, egressTime};
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

	private Map<Id<Link>, Integer> calcModeAccessTimes(Set<Id<Link>> stopLinkIds, String accessTimeZoneId, Network network) {
		Map<Id<Link>, Integer> accessTimes = new HashMap<>();
		if (accessTimeZoneId == null) {
			stopLinkIds.forEach(i -> accessTimes.put(i, 0));
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
			Object at = zone.getAttribute(accessTimeZoneId);
			return at != null ? (int) Double.parseDouble(at.toString()) : 0;
		} else {
			return 0;
		}
	}

	private Network getRoutingNetwork(String mode, Config config) {
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
					filteredNetwork = NetworkUtils.createNetwork(config);
					filter.filter(filteredNetwork, modes);
					cache.put(mode, filteredNetwork);
				}
			}
		}
		return filteredNetwork;
	}

	public RouteCharacteristics getCachedRouteCharacteristics(String mode, Facility stopFacility, Facility actFacility, RoutingModule module, Person person) {
		Id<Link> stopFacilityLinkId = stopFacility.getLinkId();
		Id<Link> actFacilityLinkId = actFacility.getLinkId();
		Map<Id<Link>, Map<Id<Link>, int[]>> modalStats = this.travelTimesDistances.get(mode);
		Map<Id<Link>, int[]> facStats = modalStats.get(stopFacilityLinkId);
		if (facStats == null) {
			throw new RuntimeException("Stop at linkId " + stopFacilityLinkId + " is not a listed stop for intermodal access egress.");
		}
		int[] value = facStats.get(actFacilityLinkId);
		int accessTime = accessTimes.getOrDefault(mode, Collections.emptyMap()).getOrDefault(stopFacilityLinkId, 0);
		if (value == null) {
			//we are slightly outside the cached radius
			List<? extends PlanElement> routeParts = module.calcRoute(DefaultRoutingRequest.withoutAttributes(stopFacility, actFacility, 3d * 3600, person));
            Leg routedLeg = TripStructureUtils.getLegs(routeParts).stream().filter(leg -> leg.getMode().equals(mode)).findFirst().orElseThrow(RuntimeException::new);
            int egressTime = getAccessTime(this.intermodalModeParams.get(mode).getAccessTimeZoneId(), scenario.getNetwork().getLinks().get(routedLeg.getRoute().getEndLinkId()).getToNode().getCoord());
            int distance = (int) routedLeg.getRoute().getDistance();
			int traveltime = (int) routedLeg.getRoute().getTravelTime().seconds();
			value = new int[]{distance, traveltime, egressTime};
			facStats.put(actFacilityLinkId, value);

		}
		return new RouteCharacteristics(value[0], accessTime, value[2], value[1] * FREESPEED_TRAVELTIME_FACTOR);
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

	private void writeCachedTraveltimes(String outputFolder) {
		for (Entry<String, Map<Id<Link>, Map<Id<Link>, int[]>>> e : this.travelTimesDistances.entrySet()) {
			String filename = outputFolder + "/intermodalCache_" + e.getKey() + ".csv.gz";
			LOGGER.info("writing intermodal cache for mode " + e.getKey() + "to File: " + filename);

			try (BufferedWriter bw = IOUtils.getBufferedWriter(filename)) {
				for (Entry<Id<Link>, Map<Id<Link>, int[]>> stop : e.getValue().entrySet()) {
					for (Entry<Id<Link>, int[]> link : stop.getValue().entrySet()) {
						bw.write(stop.getKey().toString() + ";" + link.getKey().toString() + ";" + link.getValue()[0] + ";" + link.getValue()[1] + ";" + link.getValue()[2]);
						bw.newLine();
					}
				}
				LOGGER.info("done");
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
	}
}
