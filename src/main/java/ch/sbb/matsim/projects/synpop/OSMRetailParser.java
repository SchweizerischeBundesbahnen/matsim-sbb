package ch.sbb.matsim.projects.synpop;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.osm.networkReader.PbfParser;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.utils.geometry.CoordinateTransformation;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

class OSMRetailParser {

    private static final Logger log = LogManager.getLogger(OSMRetailParser.class);
    final ExecutorService executor;
    private final CoordinateTransformation transformation;
    private Map<Long, BuildingData> buildingData;
    private Map<Long, BuildingDataWithShops> buildingDataWithShopsMap = new HashMap<>();
    private Map<Long, CompleteBuildingData> completeBuildingDataMap = new HashMap<>();
    private Map<Long, Set<Long>> buildingDataNodeStorage;
    private Map<Long, ShopData> shopDataStorage;
    private Map<Long, Coord> nodeStorage;
    private long fakeWayIdsForNodesWithoutBuildings = Long.MAX_VALUE - 1;


    OSMRetailParser(CoordinateTransformation transformation, ExecutorService executor) {
        this.transformation = transformation;
        this.executor = executor;
    }

    public static void main(String[] args) {
        OSMRetailParser osmRetailParser = new OSMRetailParser(TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, "EPSG:2056"), Executors.newWorkStealingPool());
        osmRetailParser.parse(Paths.get(args[0]));
        Zones zones = ZonesLoader.loadZones(Variables.ZONE_ID, args[1]);
        osmRetailParser.assignShopsToBuildings(zones);
        osmRetailParser.writeShopBuildingData(args[2]);
        System.out.println("done");
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

    private void writeShopBuildingData(String file) {
        String osmWay = "OSMWay";
        String area = "area";
        String floors = "floors";
        String isRetail = "isRetail";
        String x = "x";
        String y = "y";
        String shopNames = "shop_names";
        String shopOsmIds = "shop_osm_ids";
        String zoneId = "zone_id";
        try (CSVWriter writer = new CSVWriter(null, new String[]{osmWay, area, floors, isRetail, x, y, shopNames, shopOsmIds, zoneId}, file)) {
            for (CompleteBuildingData completeBuildingData : completeBuildingDataMap.values()) {
                double buildingArea = completeBuildingData.polygon().getArea();
                Coord coord = MGC.point2Coord(completeBuildingData.polygon().getCentroid());
                writer.set(osmWay, String.valueOf(completeBuildingData.osmWayId));
                writer.set(area, String.valueOf(buildingArea));
                writer.set(floors, String.valueOf(completeBuildingData.floors));
                writer.set(isRetail, String.valueOf(completeBuildingData.retail()));
                writer.set(x, String.valueOf(coord.getX()));
                writer.set(y, String.valueOf(coord.getY()));
                String shopNamesString = completeBuildingData.shops().stream().map(shopData -> shopData.name()).collect(Collectors.joining("_"));
                writer.set(shopNames, shopNamesString);
                String shopOsmNodeIds = completeBuildingData.shops().stream().map(shopData -> String.valueOf(shopData.nodeId)).collect(Collectors.joining("_"));
                writer.set(zoneId, completeBuildingData.zoneId());
                writer.set(shopOsmIds, shopOsmNodeIds);
                writer.writeRow();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }


    private void assignShopsToBuildings(Zones zones) {
        GeometryFactory fac = new GeometryFactory();
        Network allBuildingsAsNodes = NetworkUtils.createNetwork();
        for (var data : buildingData.values()) {
            Coordinate[] coords = new Coordinate[data.nodeIds.size() + 1];
            for (int i = 0; i < data.nodeIds.size(); i++) {
                long nodeid = data.nodeIds().get(i);
                Coord coord = nodeStorage.get(nodeid);
                coords[i] = new Coordinate(coord.getX(), coord.getY());
            }
            coords[data.nodeIds.size()] = coords[0];
            Polygon polygon = fac.createPolygon(coords);
            Coord centroid = MGC.point2Coord(polygon.getCentroid());
            Node node = allBuildingsAsNodes.getFactory().createNode(Id.createNodeId(data.wayId()), centroid);
            allBuildingsAsNodes.addNode(node);
            node.getAttributes().putAttribute("polygon", polygon);
        }


        shopDataStorage.values().forEach(shopData -> {
            Coord coord = nodeStorage.get(shopData.nodeId());
            var nodeCandidates = NetworkUtils.getNearestNodes(allBuildingsAsNodes, coord, 300);
            Node node = null;
            for (Node nodeCandidate : nodeCandidates) {
                Polygon polygon = (Polygon) nodeCandidate.getAttributes().getAttribute("polygon");
                if (polygon.intersects(MGC.coord2Point(coord))) {
                    node = nodeCandidate;
                    break;
                }
            }

            Long shopWayId = node != null ? Long.parseLong(node.getId().toString()) : null;
            BuildingData bd = shopWayId != null ? buildingData.get(shopWayId) : null;
            if (shopWayId == null) {
                shopWayId = fakeWayIdsForNodesWithoutBuildings--;
            }

            Zone zone = zones.findZone(coord);
            String zoneId = zone != null ? zone.getId().toString() : "";

            Polygon polygon = node != null ? (Polygon) node.getAttributes().getAttribute("polygon") : fac.createPolygon(new Coordinate[]{MGC.coord2Coordinate(coord), MGC.coord2Coordinate(coord), MGC.coord2Coordinate(coord)});

            Long finalShopWayId = shopWayId;
            CompleteBuildingData completeBuildingData = completeBuildingDataMap.computeIfAbsent(shopWayId, a -> bd != null ?
                    new CompleteBuildingData(bd.wayId, bd.nodeIds, bd.floors, bd.retail, new HashSet<>(), polygon, zoneId) :
                    new CompleteBuildingData(finalShopWayId, Collections.emptyList(), -1, false, new HashSet<>(), polygon, zoneId));
            completeBuildingData.shops.add(shopData);


        });

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
            nodeStorage.put(osmNode.getId(), transformedCoord);
        }
        if (buildingDataNodeStorage.containsKey(osmNode.getId())) {
            nodeStorage.put(osmNode.getId(), transformation.transform(new Coord(osmNode.getLongitude(), osmNode.getLatitude())));
        }

    }

    void handleWay(OsmWay osmWay) {
        Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmWay);
        if (tags.containsKey("building")) {
            List<Long> nodeIds = new ArrayList<>();
            OsmModelUtil.nodesAsList(osmWay).forEach(t -> nodeIds.add(t));
            boolean isRetail = tags.get("building").equals("retail");
            String levelString = tags.get("building:levels");
            int levels = -1;
            if (levelString != null) {
                try {
                    levels = Integer.parseInt(levelString);
                } catch (NumberFormatException e) {
                }
            }
            buildingData.put(osmWay.getId(), new BuildingData(osmWay.getId(), nodeIds, levels, isRetail));
            nodeIds.forEach(nodeId -> buildingDataNodeStorage.computeIfAbsent(nodeId, a -> new HashSet<>()).add(osmWay.getId()));

        }


    }

    record ShopData(long nodeId, Coord coord, String name) {
    }

    record BuildingData(long wayId, List<Long> nodeIds, int floors, boolean retail) {
    }

    record BuildingDataWithShops(long wayId, List<Long> nodeIds, int floors, boolean retail, Polygon polygon,
                                 Set<Long> shopNodeIds) {
    }

    record CompleteBuildingData(long osmWayId, List<Long> osmNodeIds, int floors, boolean retail, Set<ShopData> shops,
                                Polygon polygon,
                                String zoneId) {
    }


}
