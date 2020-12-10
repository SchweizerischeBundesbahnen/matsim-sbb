package ch.sbb.matsim.accessibility;

import ch.sbb.matsim.analysis.skims.RooftopUtils;
import ch.sbb.matsim.analysis.skims.RooftopUtils.ODConnection;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.routing.graph.Graph;
import ch.sbb.matsim.routing.graph.LeastCostPathTree;
import ch.sbb.matsim.routing.pt.raptor.RaptorParameters;
import ch.sbb.matsim.routing.pt.raptor.RaptorRoute;
import ch.sbb.matsim.routing.pt.raptor.RaptorRoute.RoutePart;
import ch.sbb.matsim.routing.pt.raptor.RaptorStaticConfig;
import ch.sbb.matsim.routing.pt.raptor.RaptorUtils;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptor;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorCore;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorCore.TravelInfo;
import ch.sbb.matsim.routing.pt.raptor.SwissRailRaptorData;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.MatsimEventsReader;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.algorithms.TransportModeNetworkFilter;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.router.costcalculators.OnlyTimeDependentTravelDisutility;
import org.matsim.core.router.util.TravelDisutility;
import org.matsim.core.router.util.TravelTime;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.trafficmonitoring.FreeSpeedTravelTime;
import org.matsim.core.trafficmonitoring.TravelTimeCalculator;
import org.matsim.core.utils.collections.Tuple;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.Counter;
import org.matsim.core.utils.misc.OptionalTime;
import org.matsim.pt.transitSchedule.api.TransitLine;
import org.matsim.pt.transitSchedule.api.TransitRoute;
import org.matsim.pt.transitSchedule.api.TransitSchedule;
import org.matsim.pt.transitSchedule.api.TransitScheduleReader;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;
import org.matsim.vehicles.Vehicle;

/**
 * Calculates high-resolution accessibility.
 * <p>
 * A simple approach would iterate over all required coordinates (=locations where accessibility should be calculated). Considering the fact that, for high-resolution grids, the number of coordinates
 * is a multiple of the number of transit stops, we might end up calculating the same (or very similar) travel times from transit stops multiple times. To prevent calculating the same data over and
 * over and thus to improve the calculation speed, this algorithm applies some optimization.
 * <p>
 * The optimization works as follows: - The list of coordinates is split into large blocks that cover a certain area, e.g. a 10km x 10km raster zone. - The transit stops are also assigned to the same
 * blocks, although a single stop can be part of multiple blocks as we add some buffer around the blocks (a start coordinate in the block could use a transit stop just slightly outside the block). -
 * Process each block: Compare the number of coordinates and transit stops. If the number of coordinates is considerable larger than the number of stops, use the optimized algorithm which first
 * pre-calculates connections between all stops, and then uses this data to calculate the accessibility for each coordinate. Otherwise, the unoptimized algorithm will be used which just iterates over
 * all coordinates and calculates the necessary data for each.
 */
public class Accessibility {

	private final static Logger log = Logger.getLogger(Accessibility.class);

	private final String networkFilename;
	private final String eventsFilename;
	private final String scheduleFilename;
	private final String transitNetworkFilename;
	private final Config config;
	private final Map<Coord, Double> attractions;
	private final double[] carAMDepTimes;
	private final double[] carPMDepTimes;
	private final double ptMinDepartureTime;
	private final double ptMaxDepartureTime;
	private final BiPredicate<TransitLine, TransitRoute> trainDetector;
	private final Zones zones;
	private Predicate<Link> xy2linksPredicate;
	private boolean scenarioLoaded = false;
	private Network carNetwork;
	private TravelTime tt;
	private TravelDisutility td;
	private int threadCount = 4;
	private SwissRailRaptorData raptorData;
	private TransitSchedule transitSchedule;
	private DeparturesCache departuresCache;

	public Accessibility(String networkFilename, String eventsFilename, String scheduleFilename, String transitNetworkFilename,
			Map<Coord, Double> attractions, double[] carAMDepTimes, double[] carPMDepTimes,
			double ptMinDepartureTime, double ptMaxDepartureTime, BiPredicate<TransitLine, TransitRoute> trainDetector,
			Zones zones) {
		this.networkFilename = networkFilename;
		this.eventsFilename = eventsFilename;
		this.scheduleFilename = scheduleFilename;
		this.transitNetworkFilename = transitNetworkFilename;
		this.config = ConfigUtils.createConfig();
		this.attractions = attractions;
		this.carAMDepTimes = eventsFilename == null ? new double[]{8 * 3600} : carAMDepTimes;
		this.carPMDepTimes = eventsFilename == null ? new double[0] : carPMDepTimes;
		this.ptMinDepartureTime = ptMinDepartureTime;
		this.ptMaxDepartureTime = ptMaxDepartureTime;
		this.trainDetector = trainDetector;
		this.zones = zones;
	}

	private static boolean requiresCar(Modes[] modes) {
		for (Modes mode : modes) {
			if (mode.car) {
				return true;
			}
		}
		return false;
	}

	private static boolean requiresPt(Modes[] modes) {
		for (Modes mode : modes) {
			if (mode.pt) {
				return true;
			}
		}
		return false;
	}

	private static List<ODConnection> buildODConnections(List<Map<Id<TransitStopFacility>, SwissRailRaptorCore.TravelInfo>> trees, Map<Id<TransitStopFacility>, Double> accessTimes,
			Map<Id<TransitStopFacility>, Double> egressTimes) {
		List<ODConnection> connections = new ArrayList<>();

		for (Map<Id<TransitStopFacility>, SwissRailRaptorCore.TravelInfo> tree : trees) {
			for (Map.Entry<Id<TransitStopFacility>, Double> egressEntry : egressTimes.entrySet()) {
				Id<TransitStopFacility> egressStopId = egressEntry.getKey();
				Double egressTime = egressEntry.getValue();
				SwissRailRaptorCore.TravelInfo info = tree.get(egressStopId);
				if (info != null && !info.isWalkOnly()) {
					Double accessTime = accessTimes.get(info.departureStop);
					ODConnection connection = new ODConnection(info.ptDepartureTime, info.ptTravelTime, accessTime, egressTime, info.transferCount, info);
					connections.add(connection);
				}
			}
		}

		return connections;
	}

	private static List<ODConnection> buildODConnections(ConcurrentHashMap<Id<TransitStopFacility>, IdMap<TransitStopFacility, List<ODConnection>>> cache,
			Map<Id<TransitStopFacility>, Double> accessTimes, Map<Id<TransitStopFacility>, Double> egressTimes) {
		List<ODConnection> connections = new ArrayList<>();

		for (Map.Entry<Id<TransitStopFacility>, Double> accessE : accessTimes.entrySet()) {
			Id<TransitStopFacility> fromStopId = accessE.getKey();
			double accessTime = accessE.getValue();
			IdMap<TransitStopFacility, List<ODConnection>> fromConnections = cache.get(fromStopId);
			if (fromConnections == null) {
				log.warn("no connections found for stop " + fromStopId);
				continue;
			}

			for (Map.Entry<Id<TransitStopFacility>, Double> egressE : egressTimes.entrySet()) {
				Id<TransitStopFacility> toStopId = egressE.getKey();
				double egressTime = egressE.getValue();
				List<ODConnection> fromToConnections = fromConnections.get(toStopId);
				if (fromToConnections != null) {
					for (ODConnection conn : fromToConnections) {
						connections.add(new ODConnection(conn.departureTime, conn.travelTime, accessTime, egressTime, conn.transferCount, conn.travelInfo));
					}
				}
			}

		}

		return connections;
	}

	private static Collection<TransitStopFacility> findStopCandidates(Coord coord, SwissRailRaptor raptor, RaptorParameters parameters) {
		Collection<TransitStopFacility> stops = raptor.getUnderlyingData().findNearbyStops(coord.getX(), coord.getY(), parameters.getSearchRadius());
		if (stops.isEmpty()) {
			TransitStopFacility nearest = raptor.getUnderlyingData().findNearestStop(coord.getX(), coord.getY());
			double nearestStopDistance = CoordUtils.calcEuclideanDistance(coord, nearest.getCoord());
			stops = raptor.getUnderlyingData().findNearbyStops(coord.getX(), coord.getY(), nearestStopDistance + parameters.getExtensionRadius());
		}
		return stops;
	}

	public void setXy2LinksPredicate(Predicate<Link> xy2linksPredicate) {
		this.xy2linksPredicate = xy2linksPredicate;
	}

	public void setThreadCount(int threadCount) {
		this.threadCount = threadCount;
	}

	public void calculateAccessibility(List<Coord> coordinates, Modes[] modes, File csvOutputFile) {
		boolean requiresCar = requiresCar(modes);

		if (!this.scenarioLoaded) {
			loadScenario(requiresCar);
		}

		Graph carGraph = new Graph(this.carNetwork);
		log.info("filter car-only network for assigning links to locations");
		Network xy2linksNetwork = extractXy2LinksNetwork(this.carNetwork);

		log.info("pre-create SwissRailRaptor-instances...");
		SwissRailRaptor[] raptors = new SwissRailRaptor[this.threadCount];
		for (int i = 0; i < this.threadCount; i++) {
			raptors[i] = new SwissRailRaptor(raptorData, null, null, null, null);
		}

		Map<Coord, ZoneData> zoneData = new HashMap<>();
		for (Map.Entry<Coord, Double> e : this.attractions.entrySet()) {
			double attraction = e.getValue();
			if (attraction > 0) {
				Coord coord = e.getKey();
				zoneData.put(coord, new ZoneData(
						this.carNetwork.getNodes().get(NetworkUtils.getNearestLink(xy2linksNetwork, coord).getFromNode().getId()),
						attraction
				));
			}
		}

		try (BufferedWriter writer = IOUtils.getBufferedWriter(csvOutputFile.getAbsolutePath())) {
			writer.write("X,Y");
			for (Modes mode : modes) {
				writer.write(',');
				writer.write(mode.id);
			}
			writer.write(IOUtils.NATIVE_NEWLINE);

			int blockSize = 10_000;
			Map<BlockKey, BlockData> blocks = getBlocks(coordinates, blockSize);
			log.info("Divided " + coordinates.size() + " coordinates into " + blocks.size() + " blocks.");

			int blockCnt = 0;
			for (Map.Entry<BlockKey, BlockData> e : blocks.entrySet()) {
				BlockKey key = e.getKey();
				BlockData block = e.getValue();
				blockCnt++;
				log.info("Block " + blockCnt + "/" + blocks.size() + ": " + block.toString(key, blockSize));
				int coordCount = block.coords.size();
				int stopCount = block.stops.size();
				Collection<Tuple<Coord, double[]>> results;
				if ((int) (coordCount * 1.3) > stopCount) {
					results = doOptimizedCalculation(block, carGraph, xy2linksNetwork, zoneData, raptors, modes);
				} else {
					results = doBasicCalculation(block, carGraph, xy2linksNetwork, zoneData, raptors, modes);
				}

				for (Tuple<Coord, double[]> result : results) {
					Coord coord = result.getFirst();
					double[] accessibilities = result.getSecond();
					writer.write(Double.toString(coord.getX()));
					writer.write(',');
					writer.write(Double.toString(coord.getY()));
					for (double acc : accessibilities) {
						writer.write(',');
						writer.write(Double.toString(acc));
					}
					writer.write(IOUtils.NATIVE_NEWLINE);
				}
				writer.flush();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * First, calculate for each stop in the block the best connections to every other stop in the schedule. Store these connections in a thread-safe cache. Then, calculate for each requested
	 * coordinate the necessary data. Make use of the cache to look-up possible connections, collect and filter them instead of re-calculating them.
	 */
	private Collection<Tuple<Coord, double[]>> doOptimizedCalculation(BlockData block, Graph carGraph,
			Network xy2linksNetwork, Map<Coord, ZoneData> zoneData, SwissRailRaptor[] raptors, Modes[] modes) {
		ConcurrentLinkedQueue<TransitStopFacility> stops = new ConcurrentLinkedQueue<>(block.stops);
		ConcurrentHashMap<Id<TransitStopFacility>, IdMap<TransitStopFacility, List<ODConnection>>> cache = new ConcurrentHashMap<>();

		Thread[] threads = new Thread[this.threadCount];

		Counter counter = new Counter("# stops ", " / " + block.stops.size());
		// build cache
		for (int i = 0; i < this.threadCount; i++) {
			StopWorker worker = new StopWorker(counter, stops, cache, raptors[i], RaptorUtils.createParameters(this.config), ptMinDepartureTime, ptMaxDepartureTime, transitSchedule, departuresCache);
			threads[i] = new Thread(worker, "accessibility-stops-" + i);
			threads[i].start();
		}
		for (int i = 0; i < threadCount; i++) {
			try {
				threads[i].join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		// do calculation
		ConcurrentLinkedQueue<Coord> accessibilityCoords = new ConcurrentLinkedQueue<>(block.coords);
		counter = new Counter("# coords ", " / " + block.coords.size());
		ConcurrentLinkedQueue<Tuple<Coord, double[]>> results = new ConcurrentLinkedQueue<>();
		for (int i = 0; i < this.threadCount; i++) {
			OptimizedRowWorker worker = new OptimizedRowWorker(
					this.carNetwork, carGraph, xy2linksNetwork, tt, td, this.carAMDepTimes, this.carPMDepTimes,
					cache, this.transitSchedule, this.departuresCache, raptors[i], RaptorUtils.createParameters(this.config), ptMinDepartureTime, ptMaxDepartureTime, trainDetector,
					zoneData, modes, this.zones, counter, accessibilityCoords, results);
			threads[i] = new Thread(worker, "accessibility-coords-" + i);
			threads[i].start();
		}
		for (int i = 0; i < threadCount; i++) {
			try {
				threads[i].join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		return results;
	}

	/**
	 * Just calculate for each requested coordinate the necessary data to calculate the accessibility.
	 */
	private Collection<Tuple<Coord, double[]>> doBasicCalculation(BlockData block, Graph carGraph, Network xy2linksNetwork,
			Map<Coord, ZoneData> zoneData, SwissRailRaptor[] raptors, Modes[] modes) {
		ConcurrentLinkedQueue<Coord> accessibilityCoords = new ConcurrentLinkedQueue<>(block.coords);
		ConcurrentLinkedQueue<Tuple<Coord, double[]>> results = new ConcurrentLinkedQueue<>();

		Counter counter = new Counter("#", " / " + block.coords.size());
		Thread[] threads = new Thread[this.threadCount];
		for (int i = 0; i < this.threadCount; i++) {
			RowWorker worker = new RowWorker(
					this.carNetwork, carGraph, xy2linksNetwork, tt, td, this.carAMDepTimes, this.carPMDepTimes,
					raptors[i], RaptorUtils.createParameters(this.config), ptMinDepartureTime, ptMaxDepartureTime, trainDetector,
					zoneData, modes, this.zones, counter, accessibilityCoords, results);
			threads[i] = new Thread(worker, "accessibility-" + i);
			threads[i].start();
		}
		for (int i = 0; i < threadCount; i++) {
			try {
				threads[i].join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		return results;
	}

	private Map<BlockKey, BlockData> getBlocks(Collection<Coord> coordinates, int blockSize) {
		double stopBufferSize = config.transitRouter().getSearchRadius();
		Map<Accessibility.BlockKey, Accessibility.BlockData> blocks = new HashMap<>(1000);
		for (Coord coord : coordinates) {
			int xKey = (int) (coord.getX() / blockSize);
			int yKey = (int) (coord.getY() / blockSize);
			Accessibility.BlockKey key = new Accessibility.BlockKey(xKey, yKey);
			Accessibility.BlockData block = blocks.computeIfAbsent(key, k -> new Accessibility.BlockData());
			block.coords.add(coord);
		}

		for (TransitStopFacility stop : this.transitSchedule.getFacilities().values()) {
			Coord coord = stop.getCoord();
			double x = coord.getX();
			double y = coord.getY();
			int xKey = (int) (x / blockSize);
			int yKey = (int) (y / blockSize);
			Accessibility.BlockKey key = new Accessibility.BlockKey(xKey, yKey);
			Accessibility.BlockData block = blocks.get(key);
			if (block != null) {
				block.stops.add(stop);
			}

			// check if the stop is also of interest to neighbouring blocks
			double blockMinX = xKey * blockSize;
			double blockMinY = yKey * blockSize;
			double blockMaxX = blockMinX + blockSize;
			double blockMaxY = blockMinY + blockSize;

			boolean isEast = (blockMaxX - x) <= stopBufferSize;
			boolean isWest = (x - blockMinX) <= stopBufferSize;
			boolean isNorth = (blockMaxY - y) <= stopBufferSize;
			boolean isSouth = (y - blockMinY) <= stopBufferSize;

			if (isNorth) {
				addToBlock(xKey, yKey + 1, stop, blocks);
				if (isEast) {
					addToBlock(xKey + 1, yKey + 1, stop, blocks);
				}
				if (isWest) {
					addToBlock(xKey - 1, yKey + 1, stop, blocks);
				}
			}
			if (isSouth) {
				addToBlock(xKey, yKey - 1, stop, blocks);
				if (isEast) {
					addToBlock(xKey + 1, yKey - 1, stop, blocks);
				}
				if (isWest) {
					addToBlock(xKey - 1, yKey - 1, stop, blocks);
				}
			}
			if (isEast) {
				addToBlock(xKey + 1, yKey, stop, blocks);
			}
			if (isWest) {
				addToBlock(xKey - 1, yKey, stop, blocks);
			}
		}

		return blocks;
	}

	private Network extractXy2LinksNetwork(Network network) {
		Predicate<Link> predicate = this.xy2linksPredicate;
		if (predicate == null) {
			predicate = l -> true;
		}
		Network xy2lNetwork = NetworkUtils.createNetwork();
		NetworkFactory nf = xy2lNetwork.getFactory();
		for (Link link : network.getLinks().values()) {
			if (predicate.test(link)) {
				// okay, we need that link
				Node fromNode = link.getFromNode();
				Node xy2lFromNode = xy2lNetwork.getNodes().get(fromNode.getId());
				if (xy2lFromNode == null) {
					xy2lFromNode = nf.createNode(fromNode.getId(), fromNode.getCoord());
					xy2lNetwork.addNode(xy2lFromNode);
				}
				Node toNode = link.getToNode();
				Node xy2lToNode = xy2lNetwork.getNodes().get(toNode.getId());
				if (xy2lToNode == null) {
					xy2lToNode = nf.createNode(toNode.getId(), toNode.getCoord());
					xy2lNetwork.addNode(xy2lToNode);
				}
				Link xy2lLink = nf.createLink(link.getId(), xy2lFromNode, xy2lToNode);
				xy2lLink.setAllowedModes(link.getAllowedModes());
				xy2lLink.setCapacity(link.getCapacity());
				xy2lLink.setFreespeed(link.getFreespeed());
				xy2lLink.setLength(link.getLength());
				xy2lLink.setNumberOfLanes(link.getNumberOfLanes());
				xy2lNetwork.addLink(xy2lLink);
			}
		}
		return xy2lNetwork;
	}

	private void addToBlock(int xKey, int yKey, TransitStopFacility stop, Map<BlockKey, BlockData> blocks) {
		BlockData block = blocks.get(new BlockKey(xKey, yKey));
		if (block != null) {
			block.stops.add(stop);
		}
	}

	private void loadScenario(boolean requiresCar) {
		Scenario scenario = ScenarioUtils.createScenario(this.config);
		log.info("loading network from " + networkFilename);
		new MatsimNetworkReader(scenario.getNetwork()).readFile(networkFilename);
		if (requiresCar) {
			if (eventsFilename != null) {
				log.info("extracting actual travel times from " + eventsFilename);
				TravelTimeCalculator.Builder b = new TravelTimeCalculator.Builder(scenario.getNetwork());
				b.configure(config.travelTimeCalculator());
				TravelTimeCalculator ttc = b.build();

				EventsManager events = EventsUtils.createEventsManager();
				events.addHandler(ttc);
				new MatsimEventsReader(events).readFile(eventsFilename);
				this.tt = ttc.getLinkTravelTimes();
			} else {
				this.tt = new FreeSpeedTravelTime();
				log.info("No events specified. Travel Times will be calculated with free speed travel times.");
			}

			this.td = new OnlyTimeDependentTravelDisutility(tt);

		} else {
			log.info("not loading events, as no car-accessibility needs to be calculated.");
		}
		log.info("extracting car-only network"); // this is used in any case, not only when car is needed.
		this.carNetwork = NetworkUtils.createNetwork();
		new TransportModeNetworkFilter(scenario.getNetwork()).filter(this.carNetwork, Collections.singleton(SBBModes.CAR));

		log.info("loading schedule from " + this.scheduleFilename);
		Scenario ptScenario;
		if (transitNetworkFilename.equals(networkFilename)) {
			ptScenario = scenario;
		} else {
			ptScenario = ScenarioUtils.createScenario(this.config);
			new MatsimNetworkReader(ptScenario.getNetwork()).readFile(transitNetworkFilename);
		}
		new TransitScheduleReader(ptScenario).readFile(this.scheduleFilename);
		log.info("prepare PT Matrix calculation");
		RaptorStaticConfig raptorConfig = RaptorUtils.createStaticConfig(this.config);
		raptorConfig.setOptimization(RaptorStaticConfig.RaptorOptimization.OneToAllRouting);
		this.transitSchedule = ptScenario.getTransitSchedule();
		this.raptorData = SwissRailRaptorData.create(ptScenario.getTransitSchedule(), ptScenario.getTransitVehicles(), raptorConfig, ptScenario.getNetwork(), null);
		this.departuresCache = new DeparturesCache(this.transitSchedule);

		this.scenarioLoaded = true;
	}

	private static class ZoneData {

		final Node node;
		final double attraction;

		public ZoneData(Node node, double attraction) {
			this.node = node;
			this.attraction = attraction;
		}
	}

	/**
	 * Simple implementation for TravelTime and TravelDisutility that assumes a fixed speed on all links, resulting in the shortest (and not the fastest) path to be found.
	 */
	private static class FixedSpeedTravelTimeAndDisutility implements TravelTime, TravelDisutility {

		private final double speed;

		public FixedSpeedTravelTimeAndDisutility(double speed) {
			this.speed = speed;
		}

		@Override
		public double getLinkTravelDisutility(Link link, double v, Person person, Vehicle vehicle) {
			return link.getLength() / this.speed;
		}

		@Override
		public double getLinkMinimumTravelDisutility(Link link) {
			return link.getLength() / this.speed;
		}

		@Override
		public double getLinkTravelTime(Link link, double v, Person person, Vehicle vehicle) {
			return link.getLength() / this.speed;
		}
	}

	private static class BlockData {

		final List<Coord> coords = new ArrayList<>();
		final List<TransitStopFacility> stops = new ArrayList<>();

		public String toString(BlockKey key, int blockSize) {
			StringBuilder str = new StringBuilder(50);
			str.append(key.xKey * blockSize);
			str.append('/');
			str.append(key.yKey * blockSize);
			str.append(" #coordinates = ");
			str.append(this.coords.size());
			str.append(" #stops = ");
			str.append(this.stops.size());
			return str.toString();
		}
	}

	private static class BlockKey {

		final int xKey;
		final int yKey;

		public BlockKey(int xKey, int yKey) {
			this.xKey = xKey;
			this.yKey = yKey;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o) {
				return true;
			}
			if (o == null || getClass() != o.getClass()) {
				return false;
			}
			BlockKey blockKey = (BlockKey) o;
			return xKey == blockKey.xKey &&
					yKey == blockKey.yKey;
		}

		@Override
		public int hashCode() {
			return Objects.hash(xKey, yKey);
		}
	}

	private static class RowWorker implements Runnable {

		private final boolean requiresCar;
		private final Network carNetwork;
		private final Network xy2linksNetwork;
		private final LeastCostPathTree shortestLcpTree;
		private final LeastCostPathTree[] amLcpTree;
		private final LeastCostPathTree[] pmLcpTree;
		private final double[] carAMDepTimes;
		private final double[] carPMDepTimes;
		private final SwissRailRaptor raptor;
		private final RaptorParameters parameters;
		private final double ptMinDepartureTime;
		private final double ptMaxDepartureTime;
		private final double stepSize = 120;
		private final BiPredicate<TransitLine, TransitRoute> trainDetector;
		private final Map<Coord, ZoneData> zoneData;
		private final Modes[] modes;
		private final Counter counter;
		private final Queue<Coord> coordinates;
		private final Queue<Tuple<Coord, double[]>> results;
		private Zones zones;

		RowWorker(Network carNetwork, Graph carGraph, Network xy2linksNetwork, TravelTime tt, TravelDisutility td, double[] carAMDepTimes, double[] carPMDepTimes,
				SwissRailRaptor raptor, RaptorParameters parameters, double ptMinDepartureTime, double ptMaxDepartureTime, BiPredicate<TransitLine, TransitRoute> trainDetector,
				Map<Coord, ZoneData> zoneData, Modes[] modes, Zones zones, Counter counter, Queue<Coord> coordinates, Queue<Tuple<Coord, double[]>> results) {
			this.carNetwork = carNetwork;
			this.xy2linksNetwork = xy2linksNetwork;
			this.carAMDepTimes = carAMDepTimes;
			this.carPMDepTimes = carPMDepTimes;

			FixedSpeedTravelTimeAndDisutility shortestTTD = new FixedSpeedTravelTimeAndDisutility(1.0);
			this.shortestLcpTree = new LeastCostPathTree(carGraph, shortestTTD, shortestTTD);

			this.amLcpTree = new LeastCostPathTree[carAMDepTimes.length];
			for (int i = 0; i < carAMDepTimes.length; i++) {
				this.amLcpTree[i] = new LeastCostPathTree(carGraph, tt, td);
			}
			this.pmLcpTree = new LeastCostPathTree[carPMDepTimes.length];
			for (int i = 0; i < carPMDepTimes.length; i++) {
				this.pmLcpTree[i] = new LeastCostPathTree(carGraph, tt, td);
			}
			this.raptor = raptor;
			this.parameters = parameters;
			this.ptMinDepartureTime = ptMinDepartureTime;
			this.ptMaxDepartureTime = ptMaxDepartureTime;
			this.trainDetector = trainDetector;

			this.zoneData = zoneData;
			this.requiresCar = requiresCar(modes);
			this.modes = modes;
			this.zones = zones;
			this.counter = counter;
			this.coordinates = coordinates;
			this.results = results;
		}

		public void run() {
			while (true) {
				Coord fromCoord = this.coordinates.poll();
				if (fromCoord == null) {
					return;
				}

				this.counter.incCounter();
				double[] accessibilities = calcForCoord(fromCoord);
				this.results.add(new Tuple<>(fromCoord, accessibilities));
			}
		}

		private double[] calcForCoord(Coord fromCoord) {
			Node nearestNode = this.carNetwork.getNodes().get(NetworkUtils.getNearestLink(this.xy2linksNetwork, fromCoord).getToNode().getId());

			// CAR
			if (this.requiresCar) {
				for (int i = 0; i < this.amLcpTree.length; i++) {
					this.amLcpTree[i].calculate(nearestNode.getId().index(), this.carAMDepTimes[i], null, null);
				}

				for (int i = 0; i < this.pmLcpTree.length; i++) {
					this.pmLcpTree[i].calculate(nearestNode.getId().index(), this.carPMDepTimes[i], null, null);
				}
			}

			// WALK, BIKE
			{
				this.shortestLcpTree.calculate(nearestNode.getId().index(), 8 * 3600, null, null);
			}

			// PT

			double walkSpeed = this.parameters.getBeelineWalkSpeed();

			Collection<TransitStopFacility> fromStops = findStopCandidates(fromCoord, this.raptor, this.parameters);
			Map<Id<TransitStopFacility>, Double> accessTimes = new HashMap<>();
			for (TransitStopFacility stop : fromStops) {
				double distance = CoordUtils.calcEuclideanDistance(fromCoord, stop.getCoord());
				double accessTime = distance / walkSpeed;
				accessTimes.put(stop.getId(), accessTime);
			}

			List<Map<Id<TransitStopFacility>, TravelInfo>> trees = new ArrayList<>();

			double timeWindow = this.ptMaxDepartureTime - this.ptMinDepartureTime;
			double endTime = this.ptMaxDepartureTime + timeWindow;
			for (double time = this.ptMinDepartureTime - timeWindow; time < endTime; time += this.stepSize) {

				Map<Id<TransitStopFacility>, TravelInfo> tree = this.raptor.calcTree(fromStops, time, this.parameters, null);
				trees.add(tree);
			}

			Zone fromZone = this.zones.findZone(fromCoord.getX(), fromCoord.getY());
			double carAccessTime = fromZone == null ? 0 : ((Number) fromZone.getAttribute("ACCCAR")).doubleValue(); // in seconds

			// CALCULATION

			double[] accessibility = new double[this.modes.length];
			for (Entry<Coord, ZoneData> e : this.zoneData.entrySet()) {
				Coord toCoord = e.getKey();
				ZoneData zData = e.getValue();
				Node toNode = zData.node;
				double attraction = zData.attraction;
				int toNodeIndex = toNode.getId().index();

				// CAR

				double distCar = 0;
				double amTravelTime = 0;
				double pmTravelTime = 0;
				boolean hasCar = false;
				if (requiresCar) {
					int amCount = 0;
					for (int i = 0; i < this.amLcpTree.length; i++) {
						OptionalTime time = this.amLcpTree[i].getTime(toNodeIndex);
						if (time.isDefined()) {
							amTravelTime += time.seconds() - this.carAMDepTimes[i];
							distCar += this.amLcpTree[i].getDistance(toNodeIndex);
							amCount++;
						}
					}
					amTravelTime /= Math.max(1, amCount);

					int pmCount = 0;
					for (int i = 0; i < this.pmLcpTree.length; i++) {
						OptionalTime time = this.pmLcpTree[i].getTime(toNodeIndex);
						if (time.isDefined()) {
							pmTravelTime += time.seconds() - this.carPMDepTimes[i];
							distCar += this.pmLcpTree[i].getDistance(toNodeIndex);
							pmCount++;
						}
					}
					hasCar = (amCount + pmCount) > 0;
					pmTravelTime /= Math.max(1, pmCount);
					distCar /= Math.max(1, amCount + pmCount);

					distCar /= 1000.0; // we use kilometers in the following formulas
				}

				double ttCar = Math.max(amTravelTime, pmTravelTime) / 60; // we need minutes in the following formulas
				double distCar0015 = Math.min(distCar, 15);
				double distCar1550 = Math.max(0, Math.min(distCar - 15, 35)); // 35 = 50 - 15, upperBound - lowerBound
				double distCar5099 = Math.max(0, Math.min(distCar - 50, 50)); // 50 = 100 - 50
				double distCar100x = Math.max(0, distCar - 100);

				// WALK, BIKE

				boolean hasShortestDistance = true;
				double distShortest = 0;
				{
					OptionalTime time = this.shortestLcpTree.getTime(toNodeIndex);
					if (time.isDefined()) {
						distShortest = this.shortestLcpTree.getDistance(toNodeIndex);
					} else {
						hasShortestDistance = false;
					}
				}
				distShortest /= 1000.0; // we use kilometers in the following formulas

				// PT
				Collection<TransitStopFacility> toStops = findStopCandidates(toCoord, this.raptor, this.parameters);
				Map<Id<TransitStopFacility>, Double> egressTimes = new HashMap<>();
				for (TransitStopFacility stop : toStops) {
					double distance = CoordUtils.calcEuclideanDistance(stop.getCoord(), toCoord);
					double egressTime = distance / walkSpeed;
					egressTimes.put(stop.getId(), egressTime);
				}

				List<ODConnection> connections = buildODConnections(trees, accessTimes, egressTimes);
				boolean hasPT = !connections.isEmpty();

				double ttTrain = 0;
				double ttBus = 0;
				double ptAccessTime = 0;
				double ptEgressTime = 0;
				double ptFrequency = 0;
				double ptTransfers = 0;
				double ptDistance = 0;

				if (hasPT) {
					connections = RooftopUtils.sortAndFilterConnections(connections, ptMaxDepartureTime);
					double avgAdaptionTime = RooftopUtils.calcAverageAdaptionTime(connections, ptMinDepartureTime, ptMaxDepartureTime);

					Map<ODConnection, Double> connectionShares = RooftopUtils.calcConnectionShares(connections, ptMinDepartureTime, ptMaxDepartureTime);

					float accessTime = 0;
					float egressTime = 0;
					float transferCount = 0;
					float travelTime = 0;

					double totalInVehTime = 0;
					double trainInVehTime = 0;

					for (Entry<ODConnection, Double> cs : connectionShares.entrySet()) {
						ODConnection connection = cs.getKey();
						double share = cs.getValue();

						accessTime += share * accessTimes.get(connection.travelInfo.departureStop).floatValue();
						egressTime += share * (float) connection.egressTime;
						transferCount += share * (float) connection.transferCount;
						travelTime += share * (float) connection.totalTravelTime();

						double connTotalDistance = 0;
						double connTotalInVehTime = 0;
						double connTrainInVehTime = 0;
						boolean isFirstLeg = true;

						RaptorRoute route = connection.travelInfo.getRaptorRoute();
						for (RoutePart part : route.getParts()) {
							if (part.line != null) {
								// it's a non-transfer part, an actual pt stage

								boolean isTrain = this.trainDetector.test(part.line, part.route);
								double inVehicleTime = isFirstLeg ? (part.arrivalTime - part.boardingTime) : (part.arrivalTime - part.boardingTime);
								isFirstLeg = false;

								connTotalDistance += part.distance;
								connTotalInVehTime += inVehicleTime;

								if (isTrain) {
									connTrainInVehTime += inVehicleTime;
								}
							}
						}
						ptDistance += share * connTotalDistance;

						totalInVehTime += share * connTotalInVehTime;
						trainInVehTime += share * connTrainInVehTime;
					}

					float trainShareByTravelTime = totalInVehTime > 0 ? (float) (trainInVehTime / totalInVehTime) : 0;

					ttTrain = travelTime / 60 * trainShareByTravelTime; // in minutes
					ttBus = travelTime / 60 - ttTrain; // in minutes
					ptAccessTime = accessTime / 60; // in minutes
					ptEgressTime = egressTime / 60; // in minutes
					ptFrequency = 900 / avgAdaptionTime;
					ptTransfers = transferCount;

					ptDistance /= 1000.0; // we use kilometers in the following formulas
				}
				double distPt0015 = Math.min(ptDistance, 15);
				double distPt1550 = Math.max(0, Math.min(ptDistance - 15, 35)); // 35 = 50 - 15, upperBound - lowerBound
				double distPt5099 = Math.max(0, Math.min(ptDistance - 50, 50)); // 50 = 100 - 50
				double distPt100x = Math.max(0, ptDistance - 100);

				// ACCESSIBILITY

				//                U(bike)= -0.25 + (-0.150)*dist_car/0.21667
				//
				//                U(car)  = -0.40 + (-0.053)*TT_car + (-0.040)*dist_car_0015 + (-0.040)*dist_car_1550 + 0.015*dist_car_5099 + 0.010*dist_car_100x + (-0.047)*(FROM[ACCCAR]+TO[ACCCAR])/60 + (-0.135)*TO[PCOST]*2
				//
				//                U(pt)  = +0.75 + (-0.042)*TT_bus + (-0.0378)*TT_train + (-0.015)*dist_car_0015 + (-0.015)*dist_car_1550 + 0.005*dist_car_5099 + 0.025*dist_car_100x + (-0.050)*(pt_accTime+pt_egrTime) + (-0.014)*(60/pt_freq) + (-0.227)*transfers
				//
				//                U(walk)= +2.30 + (-0.100)*dist_car/0.078336

				Zone toZone = this.zones.findZone(toCoord.getX(), toCoord.getY());
				double carEgressTime = toZone == null ? 0 : ((Number) toZone.getAttribute("ACCCAR")).doubleValue(); // in seconds
				double carParkingCost = toZone == null ? 0 : ((Number) toZone.getAttribute("PCOST")).doubleValue();

				for (int m = 0; m < this.modes.length; m++) {
					Modes modes = this.modes[m];

					double uBike = (modes.bike && hasShortestDistance) ? (-0.25 + (-0.150) * distShortest / 0.21667) : modes.missingModeUtility;
					double uCar = (modes.car && hasCar) ? (-0.40 + (-0.053) * ttCar + (-0.040) * distCar0015 + (-0.040) * distCar1550 + 0.015 * distCar5099 + 0.010 * distCar100x
							+ (-0.047) * (carAccessTime + carEgressTime) / 60 + (-0.135) * carParkingCost * 2) : modes.missingModeUtility;
					double uPt = (modes.pt && hasPT) ? (+0.75 + (-0.042) * ttBus + (-0.0378) * ttTrain + (-0.015) * distPt0015 + (-0.015) * distPt1550 + 0.005 * distPt5099 + 0.025 * distPt100x
							+ (-0.050) * (ptAccessTime + ptEgressTime) + (-0.014) * (60 / ptFrequency) + (-0.227) * ptTransfers) : modes.missingModeUtility;
					double uWalk = (modes.walk && hasShortestDistance) ? (+2.30 + (-0.100) * distShortest / 0.078336) : modes.missingModeUtility;

					double theta = modes.theta;
					double destinationUtility = Math.exp(uCar / theta) + Math.exp(uPt / theta) + Math.exp(uWalk / theta) + Math.exp(uBike / theta);

					accessibility[m] += attraction * Math.exp(theta * Math.log(destinationUtility));
				}
			}
			return accessibility;
		}
	}

	private static class OptimizedRowWorker implements Runnable {

		private final boolean requiresCar;
		private final boolean requiresPt;
		private final Network carNetwork;
		private final Network xy2linksNetwork;
		private final LeastCostPathTree shortestLcpTree;
		private final LeastCostPathTree[] amLcpTree;
		private final LeastCostPathTree[] pmLcpTree;
		private final double[] carAMDepTimes;
		private final double[] carPMDepTimes;
		private final ConcurrentHashMap<Id<TransitStopFacility>, IdMap<TransitStopFacility, List<ODConnection>>> cache;
		private final TransitSchedule transitSchedule;
		private final DeparturesCache departuresCache;
		private final SwissRailRaptor raptor;
		private final RaptorParameters parameters;
		private final double ptMinDepartureTime;
		private final double ptMaxDepartureTime;
		private final BiPredicate<TransitLine, TransitRoute> trainDetector;
		private final Map<Coord, ZoneData> zoneData;
		private final Modes[] modes;
		private final Counter counter;
		private final Queue<Coord> coordinates;
		private final Queue<Tuple<Coord, double[]>> results;
		private Zones zones;

		OptimizedRowWorker(Network carNetwork, Graph carGraph, Network xy2linksNetwork, TravelTime tt, TravelDisutility td, double[] carAMDepTimes, double[] carPMDepTimes,
				ConcurrentHashMap<Id<TransitStopFacility>, IdMap<TransitStopFacility, List<ODConnection>>> cache, TransitSchedule transitSchedule, DeparturesCache departuresCache,
				SwissRailRaptor raptor, RaptorParameters parameters, double ptMinDepartureTime, double ptMaxDepartureTime, BiPredicate<TransitLine, TransitRoute> trainDetector,
				Map<Coord, ZoneData> zoneData, Modes[] modes, Zones zones, Counter counter, Queue<Coord> coordinates, Queue<Tuple<Coord, double[]>> results) {
			this.carNetwork = carNetwork;
			this.xy2linksNetwork = xy2linksNetwork;
			this.carAMDepTimes = carAMDepTimes;
			this.carPMDepTimes = carPMDepTimes;

			FixedSpeedTravelTimeAndDisutility shortestTTD = new FixedSpeedTravelTimeAndDisutility(1.0);
			this.shortestLcpTree = new LeastCostPathTree(carGraph, shortestTTD, shortestTTD);

			this.amLcpTree = new LeastCostPathTree[carAMDepTimes.length];
			for (int i = 0; i < carAMDepTimes.length; i++) {
				this.amLcpTree[i] = new LeastCostPathTree(carGraph, tt, td);
			}
			this.pmLcpTree = new LeastCostPathTree[carPMDepTimes.length];
			for (int i = 0; i < carPMDepTimes.length; i++) {
				this.pmLcpTree[i] = new LeastCostPathTree(carGraph, tt, td);
			}
			this.cache = cache;
			this.transitSchedule = transitSchedule;
			this.departuresCache = departuresCache;
			this.raptor = raptor;
			this.parameters = parameters;
			this.ptMinDepartureTime = ptMinDepartureTime;
			this.ptMaxDepartureTime = ptMaxDepartureTime;
			this.trainDetector = trainDetector;

			this.zoneData = zoneData;
			this.requiresCar = requiresCar(modes);
			this.requiresPt = requiresPt(modes);
			this.modes = modes;
			this.zones = zones;
			this.counter = counter;
			this.coordinates = coordinates;
			this.results = results;
		}

		public void run() {
			while (true) {
				Coord fromCoord = this.coordinates.poll();
				if (fromCoord == null) {
					return;
				}

				this.counter.incCounter();
				double[] accessibilities = calcForCoord(fromCoord);
				this.results.add(new Tuple<>(fromCoord, accessibilities));
			}
		}

		private double[] calcForCoord(Coord fromCoord) {
			Node nearestNode = this.carNetwork.getNodes().get(NetworkUtils.getNearestLink(this.xy2linksNetwork, fromCoord).getToNode().getId());

			// CAR
			double carAccessTime = Double.NaN;
			if (this.requiresCar) {
				for (int i = 0; i < this.amLcpTree.length; i++) {
					this.amLcpTree[i].calculate(nearestNode.getId().index(), this.carAMDepTimes[i], null, null);
				}

				for (int i = 0; i < this.pmLcpTree.length; i++) {
					this.pmLcpTree[i].calculate(nearestNode.getId().index(), this.carPMDepTimes[i], null, null);
				}

				Zone fromZone = this.zones.findZone(fromCoord.getX(), fromCoord.getY());
				carAccessTime = fromZone == null ? 0 : ((Number) fromZone.getAttribute("ACCCAR")).doubleValue(); // in seconds
			}

			// WALK, BIKE
			{
				this.shortestLcpTree.calculate(nearestNode.getId().index(), 8 * 3600, null, null);
			}

			// PT
			double walkSpeed = this.parameters.getBeelineWalkSpeed();
			Map<Id<TransitStopFacility>, Double> accessTimes = new HashMap<>();
			if (this.requiresPt) {
				List<TransitStopFacility> missingStops = new ArrayList<>();
				Collection<TransitStopFacility> fromStops = findStopCandidates(fromCoord, this.raptor, this.parameters);
				for (TransitStopFacility stop : fromStops) {
					double distance = CoordUtils.calcEuclideanDistance(fromCoord, stop.getCoord());
					double accessTime = distance / walkSpeed;
					accessTimes.put(stop.getId(), accessTime);
					if (!cache.containsKey(stop.getId())) {
						missingStops.add(stop);
					}
				}
				if (!missingStops.isEmpty()) {
					for (TransitStopFacility fromStop : missingStops) {
						if (this.cache.containsKey(fromStop.getId())) {
							continue;
						}
						log.info(Thread.currentThread().getName() + " missing stop: " + fromStop.getId() + " " + fromStop.getName());
						List<Map<Id<TransitStopFacility>, TravelInfo>> trees = new ArrayList<>();

						double mainTimeWindow = this.ptMaxDepartureTime - this.ptMinDepartureTime;
						double time = this.ptMinDepartureTime - mainTimeWindow;
						double endTime = this.ptMaxDepartureTime + mainTimeWindow;
						while (time < endTime) {
							time = this.departuresCache.getNextDepartureTime(fromStop.getId(), time).seconds();

							Map<Id<TransitStopFacility>, TravelInfo> tree = this.raptor.calcTree(fromStop, time, this.parameters, null);
							trees.add(tree);

							time += 60; // +1 minute
						}

						IdMap<TransitStopFacility, List<ODConnection>> connectionsPerStop = new IdMap<>(TransitStopFacility.class);
						for (TransitStopFacility toStop : this.transitSchedule.getFacilities().values()) {
							List<ODConnection> connections = new ArrayList<>();

							for (Map<Id<TransitStopFacility>, SwissRailRaptorCore.TravelInfo> tree : trees) {
								Id<TransitStopFacility> egressStopId = toStop.getId();
								SwissRailRaptorCore.TravelInfo info = tree.get(egressStopId);
								if (info != null && !info.isWalkOnly()) {
									ODConnection connection = new ODConnection(info.ptDepartureTime, info.ptTravelTime, 0, 0, info.transferCount, info);
									connections.add(connection);
								}
							}

							connections = RooftopUtils.sortAndFilterConnections(connections, ptMaxDepartureTime);
							connectionsPerStop.put(toStop.getId(), connections);
						}
						this.cache.put(fromStop.getId(), connectionsPerStop);
						log.info(Thread.currentThread() + " update cache with " + fromStop.getId() + " " + fromStop.getName());
					}
				}
			}

			// CALCULATION

			double[] accessibility = new double[this.modes.length];
			for (Entry<Coord, ZoneData> e : this.zoneData.entrySet()) {
				Coord toCoord = e.getKey();
				ZoneData zData = e.getValue();
				Node toNode = zData.node;
				double attraction = zData.attraction;
				int toNodeIndex = toNode.getId().index();

				// CAR

				double distCar = 0;
				double amTravelTime = 0;
				double pmTravelTime = 0;
				boolean hasCar = false;
				if (requiresCar) {
					int amCount = 0;
					for (int i = 0; i < this.amLcpTree.length; i++) {
						OptionalTime time = this.amLcpTree[i].getTime(toNodeIndex);
						if (time.isDefined()) {
							amTravelTime += time.seconds() - this.carAMDepTimes[i];
							distCar += this.amLcpTree[i].getDistance(toNodeIndex);
							amCount++;
						}
					}
					amTravelTime /= Math.max(1, amCount);

					int pmCount = 0;
					for (int i = 0; i < this.pmLcpTree.length; i++) {
						OptionalTime time = this.pmLcpTree[i].getTime(toNodeIndex);
						if (time.isDefined()) {
							pmTravelTime += time.seconds() - this.carPMDepTimes[i];
							distCar += this.pmLcpTree[i].getDistance(toNodeIndex);
							pmCount++;
						}
					}
					hasCar = (amCount + pmCount) > 0;
					pmTravelTime /= Math.max(1, pmCount);
					distCar /= Math.max(1, amCount + pmCount);

					distCar /= 1000.0; // we use kilometers in the following formulas
				}

				double ttCar = Math.max(amTravelTime, pmTravelTime) / 60; // we need minutes in the following formulas
				double distCar0015 = Math.min(distCar, 15);
				double distCar1550 = Math.max(0, Math.min(distCar - 15, 35)); // 35 = 50 - 15, upperBound - lowerBound
				double distCar5099 = Math.max(0, Math.min(distCar - 50, 50)); // 50 = 100 - 50
				double distCar100x = Math.max(0, distCar - 100);

				// WALK, BIKE

				boolean hasShortestDistance = true;
				double distShortest = 0;
				{
					OptionalTime time = this.shortestLcpTree.getTime(toNodeIndex);
					if (time.isDefined()) {
						distShortest = this.shortestLcpTree.getDistance(toNodeIndex);
					} else {
						hasShortestDistance = false;
					}
				}
				distShortest /= 1000.0; // we use kilometers in the following formulas

				// PT

				boolean hasPT = false;
				double ttTrain = 0;
				double ttBus = 0;
				double ptAccessTime = 0;
				double ptEgressTime = 0;
				double ptFrequency = 0;
				double ptTransfers = 0;
				double ptDistance = 0;
				if (this.requiresPt) {

					Collection<TransitStopFacility> toStops = findStopCandidates(toCoord, this.raptor, this.parameters);
					Map<Id<TransitStopFacility>, Double> egressTimes = new HashMap<>();
					for (TransitStopFacility stop : toStops) {
						double distance = CoordUtils.calcEuclideanDistance(stop.getCoord(), toCoord);
						double egressTime = distance / walkSpeed;
						egressTimes.put(stop.getId(), egressTime);
					}

					List<ODConnection> connections = buildODConnections(this.cache, accessTimes, egressTimes);

					hasPT = !connections.isEmpty();
					if (hasPT) {

						connections = RooftopUtils.sortAndFilterConnections(connections, ptMaxDepartureTime);

						double avgAdaptionTime = RooftopUtils.calcAverageAdaptionTime(connections, ptMinDepartureTime, ptMaxDepartureTime);

						Map<ODConnection, Double> connectionShares = RooftopUtils.calcConnectionShares(connections, ptMinDepartureTime, ptMaxDepartureTime);

						float accessTime = 0;
						float egressTime = 0;
						float transferCount = 0;
						float travelTime = 0;

						double totalInVehTime = 0;
						double trainInVehTime = 0;

						for (Entry<ODConnection, Double> cs : connectionShares.entrySet()) {
							ODConnection connection = cs.getKey();
							double share = cs.getValue();

							accessTime += share * accessTimes.get(connection.travelInfo.departureStop).floatValue();
							egressTime += share * (float) connection.egressTime;
							transferCount += share * (float) connection.transferCount;
							travelTime += share * (float) connection.totalTravelTime();

							double connTotalDistance = 0;
							double connTotalInVehTime = 0;
							double connTrainInVehTime = 0;
							boolean isFirstLeg = true;

							RaptorRoute route = connection.travelInfo.getRaptorRoute();
							for (RoutePart part : route.getParts()) {
								if (part.line != null) {
									// it's a non-transfer part, an actual pt stage

									boolean isTrain = this.trainDetector.test(part.line, part.route);
									double inVehicleTime = isFirstLeg ? (part.arrivalTime - part.boardingTime) : (part.arrivalTime - part.boardingTime);
									isFirstLeg = false;

									connTotalDistance += part.distance;
									connTotalInVehTime += inVehicleTime;

									if (isTrain) {
										connTrainInVehTime += inVehicleTime;
									}
								}
							}
							ptDistance += share * connTotalDistance;

							totalInVehTime += share * connTotalInVehTime;
							trainInVehTime += share * connTrainInVehTime;
						}

						float trainShareByTravelTime = totalInVehTime > 0 ? (float) (trainInVehTime / totalInVehTime) : 0;

						ttTrain = travelTime / 60 * trainShareByTravelTime; // in minutes
						ttBus = travelTime / 60 - ttTrain; // in minutes
						ptAccessTime = accessTime / 60; // in minutes
						ptEgressTime = egressTime / 60; // in minutes
						ptFrequency = 900 / avgAdaptionTime;
						ptTransfers = transferCount;

						ptDistance /= 1000.0; // we use kilometers in the following formulas
					}
				}
				double distPt0015 = Math.min(ptDistance, 15);
				double distPt1550 = Math.max(0, Math.min(ptDistance - 15, 35)); // 35 = 50 - 15, upperBound - lowerBound
				double distPt5099 = Math.max(0, Math.min(ptDistance - 50, 50)); // 50 = 100 - 50
				double distPt100x = Math.max(0, ptDistance - 100);

				// ACCESSIBILITY

				//                U(bike)= -0.25 + (-0.150)*dist_car/0.21667
				//
				//                U(car)  = -0.40 + (-0.053)*TT_car + (-0.040)*dist_car_0015 + (-0.040)*dist_car_1550 + 0.015*dist_car_5099 + 0.010*dist_car_100x + (-0.047)*(FROM[ACCCAR]+TO[ACCCAR])/60 + (-0.135)*TO[PCOST]*2
				//
				//                U(pt)  = +0.75 + (-0.042)*TT_bus + (-0.0378)*TT_train + (-0.015)*dist_car_0015 + (-0.015)*dist_car_1550 + 0.005*dist_car_5099 + 0.025*dist_car_100x + (-0.050)*(pt_accTime+pt_egrTime) + (-0.014)*(60/pt_freq) + (-0.227)*transfers
				//
				//                U(walk)= +2.30 + (-0.100)*dist_car/0.078336

				Zone toZone = this.zones.findZone(toCoord.getX(), toCoord.getY());
				double carEgressTime = toZone == null ? 0 : ((Number) toZone.getAttribute("ACCCAR")).doubleValue(); // in seconds
				double carParkingCost = toZone == null ? 0 : ((Number) toZone.getAttribute("PCOST")).doubleValue();

				for (int m = 0; m < this.modes.length; m++) {
					Modes modes = this.modes[m];

					double uBike = (modes.bike && hasShortestDistance) ? (-0.25 + (-0.150) * distShortest / 0.21667) : modes.missingModeUtility;
					double uCar = (modes.car && hasCar) ? (-0.40 + (-0.053) * ttCar + (-0.040) * distCar0015 + (-0.040) * distCar1550 + 0.015 * distCar5099 + 0.010 * distCar100x
							+ (-0.047) * (carAccessTime + carEgressTime) / 60 + (-0.135) * carParkingCost * 2) : modes.missingModeUtility;
					double uPt = (modes.pt && hasPT) ? (+0.75 + (-0.042) * ttBus + (-0.0378) * ttTrain + (-0.015) * distPt0015 + (-0.015) * distPt1550 + 0.005 * distPt5099 + 0.025 * distPt100x
							+ (-0.050) * (ptAccessTime + ptEgressTime) + (-0.014) * (60 / ptFrequency) + (-0.227) * ptTransfers) : modes.missingModeUtility;
					double uWalk = (modes.walk && hasShortestDistance) ? (+2.30 + (-0.100) * distShortest / 0.078336) : modes.missingModeUtility;

					double theta = modes.theta;
					double destinationUtility = Math.exp(uCar / theta) + Math.exp(uPt / theta) + Math.exp(uWalk / theta) + Math.exp(uBike / theta);

					accessibility[m] += attraction * Math.exp(theta * Math.log(destinationUtility));
				}
			}
			return accessibility;
		}
	}

	private static class StopWorker implements Runnable {

		private final Counter counter;
		private final ConcurrentLinkedQueue<TransitStopFacility> stops;
		private final ConcurrentHashMap<Id<TransitStopFacility>, IdMap<TransitStopFacility, List<ODConnection>>> cache;

		private final SwissRailRaptor raptor;
		private final RaptorParameters parameters;
		private final double ptMinDepartureTime;
		private final double ptMaxDepartureTime;
		private final TransitSchedule transitSchedule;
		private final DeparturesCache departuresCache;

		StopWorker(Counter counter, ConcurrentLinkedQueue<TransitStopFacility> stops,
				ConcurrentHashMap<Id<TransitStopFacility>, IdMap<TransitStopFacility, List<ODConnection>>> cache,
				SwissRailRaptor raptor, RaptorParameters parameters, double ptMinDepartureTime, double ptMaxDepartureTime,
				TransitSchedule transitSchedule, DeparturesCache departuresCache) {
			this.counter = counter;
			this.stops = stops;
			this.cache = cache;

			this.raptor = raptor;
			this.parameters = parameters;
			this.ptMinDepartureTime = ptMinDepartureTime;
			this.ptMaxDepartureTime = ptMaxDepartureTime;
			this.transitSchedule = transitSchedule;
			this.departuresCache = departuresCache;
		}

		@Override
		public void run() {
			while (true) {
				TransitStopFacility stop = this.stops.poll();
				if (stop == null) {
					return;
				}

				this.counter.incCounter();
				calcForStop(stop);
			}

		}

		private void calcForStop(TransitStopFacility fromStop) {
			List<Map<Id<TransitStopFacility>, TravelInfo>> trees = new ArrayList<>();

			// to calculate the correct adaption times, we need to expand the time window to also catch departures before and after the time window of interest.
			double mainTimeWindow = this.ptMaxDepartureTime - this.ptMinDepartureTime;
			double time = this.ptMinDepartureTime - mainTimeWindow;
			double endTime = this.ptMaxDepartureTime + mainTimeWindow;
			while (time < endTime) {
				time = this.departuresCache.getNextDepartureTime(fromStop.getId(), time).seconds();

				Map<Id<TransitStopFacility>, TravelInfo> tree = this.raptor.calcTree(fromStop, time, this.parameters, null);
				trees.add(tree);

				time += 60; // +1 minute
			}

			IdMap<TransitStopFacility, List<ODConnection>> connectionsPerStop = new IdMap<>(TransitStopFacility.class);
			for (TransitStopFacility toStop : this.transitSchedule.getFacilities().values()) {
				List<ODConnection> connections = new ArrayList<>();

				for (Map<Id<TransitStopFacility>, SwissRailRaptorCore.TravelInfo> tree : trees) {
					Id<TransitStopFacility> egressStopId = toStop.getId();
					SwissRailRaptorCore.TravelInfo info = tree.get(egressStopId);
					if (info != null && !info.isWalkOnly()) {
						ODConnection connection = new ODConnection(info.ptDepartureTime, info.ptTravelTime, 0, 0, info.transferCount, info);
						connections.add(connection);
					}
				}

				connections = RooftopUtils.sortAndFilterConnections(connections, ptMaxDepartureTime);
				connectionsPerStop.put(toStop.getId(), connections);
			}
			this.cache.put(fromStop.getId(), connectionsPerStop);
		}
	}

	public static class Modes {

		private String id;
		private boolean car;
		private boolean pt;
		private boolean walk;
		private boolean bike;
		private double missingModeUtility = -9999;
		private double theta = 1;

		public Modes(String id, boolean car, boolean pt, boolean walk, boolean bike) {
			this.id = id;
			this.car = car;
			this.pt = pt;
			this.walk = walk;
			this.bike = bike;
		}
	}

}
