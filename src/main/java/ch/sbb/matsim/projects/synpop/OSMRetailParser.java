package ch.sbb.matsim.projects.synpop;

import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Coord;
import org.matsim.contrib.osm.networkReader.LinkProperties;
import org.matsim.core.utils.geometry.CoordinateTransformation;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.function.BiPredicate;

class OSMRetailParser {

	private static final Logger log = LogManager.getLogger(OSMRetailParser.class);
	final ExecutorService executor;
	private final CoordinateTransformation transformation;
	private final Map<String, LinkProperties> linkProperties;
	private final BiPredicate<Coord, Integer> linkFilter;
	private Map<Long, BuildingData> buildingData;
	private Map<Long, Set<Long>> buildingDataNodeStorage;
	private Map<Long, ShopData> shopDataStorage;
	private Map<Long, Coord> nodeStorage;

	OSMRetailParser(CoordinateTransformation transformation, Map<String, LinkProperties> linkProperties, BiPredicate<Coord, Integer> linkFilter, ExecutorService executor) {
		this.transformation = transformation;
		this.linkProperties = linkProperties;
		this.linkFilter = linkFilter;
		this.executor = executor;
	}


	void parse(Path inputFile) {
		buildingData = new ConcurrentHashMap<>();
		buildingDataNodeStorage = new ConcurrentHashMap<>();
		shopDataStorage = new ConcurrentHashMap<>();
		nodeStorage = new ConcurrentHashMap<>();
		// make sure we have empty collections

		log.info("Starting to read ways ");

		new PbfParser.Builder()
				.setWaysHandler(this::handleWay)
				.setExecutor(executor)
				.build()
				.parse(inputFile);

		log.info("Finished reading ways");

		log.info("Starting to read nodes");

		new PbfParser.Builder()
				.setNodeHandler(this::handleNode)
				.setExecutor(executor)
				.build()
				.parse(inputFile);

		log.info("finished reading nodes");
	}

	void handleNode(OsmNode osmNode) {
		var tags = OsmModelUtil.getTagsAsMap(osmNode);
		if (tags.containsKey("shop")) {
			Coord transformedCoord = transformation.transform(new Coord(osmNode.getLongitude(), osmNode.getLatitude()));
			String name = tags.get("name");
			String branch = tags.get("branch");
			String shopName = name != null ? name : "" + " " + name != null ? branch : "";
			var shopData = new ShopData(osmNode.getId(), transformedCoord, shopName);
			shopDataStorage.put(osmNode.getId(), shopData);
		}

	}

	void handleWay(OsmWay osmWay) {
		Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmWay);
		if (tags.containsKey("building")) {
			List<Long> nodeIds = new ArrayList<>();
			OsmModelUtil.nodesAsList(osmWay).forEach(t -> nodeIds.add(t));
			String levelString = tags.get("building:levels");
			int levels = -1;
			if (levelString != null) {
				try {
					levels = Integer.parseInt(levelString);
				} catch (NumberFormatException e) {
				}
			}
			buildingData.put(osmWay.getId(), new BuildingData(osmWay.getId(), nodeIds, levels));
			nodeIds.forEach(nodeId -> buildingDataNodeStorage.computeIfAbsent(nodeId, a -> new HashSet<>()).add(osmWay.getId()));

		}


	}

	record ShopData(long nodeId, Coord coord, String name) {
	}

	record BuildingData(long wayId, List<Long> nodeIds, int floors) {
	}


}
