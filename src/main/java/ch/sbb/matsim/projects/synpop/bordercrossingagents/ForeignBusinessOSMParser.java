package ch.sbb.matsim.projects.synpop.bordercrossingagents;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesImpl;
import ch.sbb.matsim.zones.ZonesLoader;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;
import org.apache.commons.lang3.mutable.MutableDouble;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.contrib.common.util.DistanceUtils;
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

class ForeignBusinessOSMParser {

    private static final Logger log = LogManager.getLogger(ForeignBusinessOSMParser.class);
    final ExecutorService executor;
    private final CoordinateTransformation transformation;
    private final Zones zones;
    private final Set<Coord> borderpoints = new HashSet<>();
    private final Map<Id<Zone>, Double> distancesToSwissBorder = new HashMap<>();
    private final Map<Id<Zone>, MutableInt> shopsPerZone = new HashMap<>();
    private final Map<Id<Zone>, MutableDouble> retailAreaPerZone = new HashMap<>();
    final double maximumAttraction = 368264.6396;
    //reference zone: Basel downtown (270101062)
    final double referenceAttractionLeisure = 5095.0;
    final double referenceAttractionShopping = 8823.0;
    final double referenceAttractionAccompany = 1472.0;
    final double referenceAttractionOther = 231.0;
    final double referenceAttractionBusiness = 120.0;

    //  visit_L	visit_S	visit_S_lt	visit_S_st
//2127	31077	28251	2826

    final double reference_visit_L = 2127;
    final double reference_visit_S = 31077;
    final double reference_visit_S_lt = 0.9 * reference_visit_S;
    final double reference_visit_S_st = 0.1 * reference_visit_S;

    private Map<Long, BuildingData> buildingData;
    private final Map<Long, CompleteBuildingData> completeBuildingDataMap = new HashMap<>();
    private Map<Long, Set<Long>> buildingDataNodeStorage;
    private Map<Long, ShopData> shopDataStorage;
    private Map<Long, Coord> nodeStorage;
    private long fakeWayIdsForNodesWithoutBuildings = Long.MAX_VALUE - 1;


    ForeignBusinessOSMParser(CoordinateTransformation transformation, ExecutorService executor, Zones zones) {
        this.transformation = transformation;
        this.executor = executor;
        this.zones = zones;
    }

    public static void main(String[] args) {
        String osmFile = args[0];
        String zonesFile = args[1];
        String borderPointsFile = args[2];
        String businessesFile = args[3];
        String zonalAggregateFile = args[4];

        Zones zones = ZonesLoader.loadZones("ID_Zone", zonesFile, "ID_Zone");
        ForeignBusinessOSMParser osmRetailParser = new ForeignBusinessOSMParser(TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, TransformationFactory.CH1903_LV03_Plus), Executors.newWorkStealingPool(), zones);
        osmRetailParser.parseBorderPoints(borderPointsFile);
        osmRetailParser.prepareZonalBorderDistances();
        osmRetailParser.parse(Paths.get(osmFile));
        osmRetailParser.assignShopsToBuildings();
        osmRetailParser.writeShopBuildingData(businessesFile);
        osmRetailParser.writeZonalRetailAttractions(zonalAggregateFile);
        System.out.println("done");
    }

    private void writeZonalRetailAttractions(String outputFile) {
        String[] columns = new String[]{"zone_id", "businesses", "retailArea", "distanceToBorder", "area", "attraction", "Attr_L", "Attr_S", "Attr_B", "Attr_O", "Attr_A", "visit_L", "visit_S", "visit_S_lt", "visit_S_st", "Attr_EC"};
        try (CSVWriter writer = new CSVWriter(null, columns, outputFile)) {
            for (var entry : this.distancesToSwissBorder.entrySet()) {
                if (entry.getValue() > 0.0) {
                    writer.set("zone_id", entry.getKey().toString());
                    writer.set("distanceToBorder", String.valueOf((int) entry.getValue().doubleValue()));
                    writer.set("businesses", this.shopsPerZone.get(entry.getKey()).toString());
                    Double retailArea = this.retailAreaPerZone.get(entry.getKey()).doubleValue();
                    double attraction = retailArea / Math.pow(entry.getValue() * 0.001, 2);
                    double attr_l = calculateLeisureAttraction(attraction);
                    double attr_s = calculateShoppingAttraction(attraction);
                    double attr_o = calculateOtherAttraction(attraction);
                    double attr_b = calculateBusinessAttraction(attraction);
                    double attr_a = calculateAccompanyAttraction(attraction);

                    int visit_L = (int) Math.round(reference_visit_L * (attr_l / referenceAttractionLeisure));
                    int visit_S = (int) Math.round(reference_visit_S * (attr_s / referenceAttractionShopping));
                    int visit_S_lt = (int) Math.round(reference_visit_S_lt * (attr_s / referenceAttractionShopping));
                    int visit_S_st = (int) Math.round(reference_visit_S_st * (attr_s / referenceAttractionShopping));

                    writer.set("retailArea", retailArea.toString());
                    writer.set("attraction", String.valueOf(attraction));
                    writer.set("Attr_L", String.valueOf(attr_l));
                    writer.set("Attr_S", String.valueOf(attr_s));
                    writer.set("Attr_O", String.valueOf(attr_o));
                    writer.set("Attr_B", String.valueOf(attr_b));
                    writer.set("Attr_EC", "0");
                    writer.set("Attr_A", String.valueOf(attr_a));
                    writer.set("visit_L", String.valueOf(visit_L));
                    writer.set("visit_S", String.valueOf(visit_S));
                    writer.set("visit_S_lt", String.valueOf(visit_S_lt));
                    writer.set("visit_S_st", String.valueOf(visit_S_st));

                    writer.set("area", String.valueOf(this.zones.getZone(entry.getKey()).getEnvelope().getArea() / 1000000.0));
                    writer.writeRow();
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private double calculateLeisureAttraction(double attraction) {
        return (attraction / maximumAttraction) * referenceAttractionLeisure;
    }

    private double calculateShoppingAttraction(double attraction) {
        return (attraction / maximumAttraction) * referenceAttractionShopping;
    }

    private double calculateOtherAttraction(double attraction) {
        return (attraction / maximumAttraction) * referenceAttractionOther;
    }

    private double calculateAccompanyAttraction(double attraction) {
        return (attraction / maximumAttraction) * referenceAttractionAccompany;
    }

    private double calculateBusinessAttraction(double attraction) {
        return (attraction / maximumAttraction) * referenceAttractionBusiness;
    }


    private void prepareZonalBorderDistances() {
        for (Zone zone : ((ZonesImpl) zones).getZones()) {
            double distanceToBorder = 0.0;
            if (Integer.parseInt(zone.getId().toString()) >= 710101001) {
                Coord zoneCenter = MGC.coordinate2Coord(zone.getEnvelope().centre());
                distanceToBorder = findClosestBorderPointDistance(zoneCenter);
            }
            distancesToSwissBorder.put(zone.getId(), distanceToBorder);
            retailAreaPerZone.put(zone.getId(), new MutableDouble());
            shopsPerZone.put(zone.getId(), new MutableInt());
        }
    }

    private double findClosestBorderPointDistance(Coord zoneCenter) {
        double minDistance = this.borderpoints.stream()
                .mapToDouble(coord -> DistanceUtils.calculateSquaredDistance(zoneCenter, coord))
                .min()
                .getAsDouble();
        return Math.sqrt(minDistance);
    }

    private void parseBorderPoints(String borderPointsFile) {
        try (CSVReader reader = new CSVReader(borderPointsFile, ";")) {
            var line = reader.readLine();
            while (line != null) {
                double x = Double.parseDouble(line.get("x"));
                double y = Double.parseDouble(line.get("y"));
                this.borderpoints.add(new Coord(x, y));
                line = reader.readLine();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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
        String business_id = "business_id";
        String x = "xcoord";
        String y = "ycoord";
        String zoneId = "zone_id";
        String sector = "sector";
        String noga = "noga_code";
        String school_type = "school_type";
        String jobs_endo = "jobs_endo";
        String fte_endo = "fte_endo";
        String jobs_exo = "jobs_exo";
        String fte_exo = "fte_exo";
        String area = "area";
        String attraction = "relative_attraction";
        String osmWay = "osm_way";
        String no_of_shops = "no_of_shops";


        try (CSVWriter writer = new CSVWriter(null, new String[]{business_id, zoneId, sector, noga, school_type, jobs_endo, fte_endo, jobs_exo, fte_exo, x, y, osmWay, area, no_of_shops, attraction}, file)) {
            int businessId = 9_100_000;
            for (CompleteBuildingData completeBuildingData : completeBuildingDataMap.values()) {
                double buildingArea = completeBuildingData.polygon().getArea();
                Coord coord = MGC.point2Coord(completeBuildingData.polygon().getCentroid());
                writer.set(business_id, String.valueOf(businessId));
                writer.set(osmWay, String.valueOf(completeBuildingData.osmWayId));
                writer.set(area, String.valueOf(buildingArea));
                writer.set(attraction, String.valueOf(Math.max(buildingArea / 33.33, 1.0)));
                writer.set(sector, "retail");
                writer.set(noga, "563002");
                writer.set(school_type, "no_school");
                writer.set(jobs_endo, "0");
                writer.set(fte_endo, "0");
                writer.set(jobs_exo, "0");
                writer.set(fte_exo, "0");
                writer.set(no_of_shops, String.valueOf(completeBuildingData.shops.size()));
                writer.set(x, String.valueOf(coord.getX()));
                writer.set(y, String.valueOf(coord.getY()));
                writer.set(zoneId, completeBuildingData.zoneId());
                writer.writeRow();
                businessId++;
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }


    private void assignShopsToBuildings() {
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
            if (zone != null) {
                String zoneId = zone.getId().toString();
                int zoneIdNo = Integer.parseInt(zoneId);
                //include Buesingen and Campione, exclude Liechtenstein
                if (zoneIdNo >= 710101001) {
                    Polygon polygon = node != null ? (Polygon) node.getAttributes().getAttribute("polygon") : fac.createPolygon(new Coordinate[]{MGC.coord2Coordinate(coord), MGC.coord2Coordinate(coord), MGC.coord2Coordinate(coord)});

                    Long finalShopWayId = shopWayId;
                    CompleteBuildingData completeBuildingData = completeBuildingDataMap.computeIfAbsent(shopWayId, a -> bd != null ?
                            new CompleteBuildingData(bd.wayId, bd.nodeIds, bd.floors, bd.retail, new HashSet<>(), polygon, zoneId) :
                            new CompleteBuildingData(finalShopWayId, Collections.emptyList(), -1, false, new HashSet<>(), polygon, zoneId));
                    completeBuildingData.shops.add(shopData);

                }
            }
        });
        completeBuildingDataMap.values().forEach(buildingData -> {
            Id<Zone> zoneId = Id.create(buildingData.zoneId, Zone.class);
            this.shopsPerZone.get(zoneId).add(buildingData.shops.size());
            this.retailAreaPerZone.get(zoneId).add(buildingData.polygon.getArea());

        });

    }

    void handleNode(OsmNode osmNode) {
        var tags = OsmModelUtil.getTagsAsMap(osmNode);
        if (tags.containsKey("shop")) {
            Coord transformedCoord = transformation.transform(new Coord(osmNode.getLongitude(), osmNode.getLatitude()));
            String name = tags.get("name");
            String branch = tags.get("branch");
            String shopName = name != null ? name : " " + name != null ? branch : "";
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

    record CompleteBuildingData(long osmWayId, List<Long> osmNodeIds, int floors, boolean retail, Set<ShopData> shops,
                                Polygon polygon,
                                String zoneId) {
    }


}
