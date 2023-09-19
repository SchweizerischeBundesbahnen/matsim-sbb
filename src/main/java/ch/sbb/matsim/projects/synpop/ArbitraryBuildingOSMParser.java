package ch.sbb.matsim.projects.synpop;

import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
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
import org.matsim.contrib.common.util.WeightedRandomSelection;
import org.matsim.contrib.osm.networkReader.PbfParser;
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

class ArbitraryBuildingOSMParser {

    private static final Logger log = LogManager.getLogger(ArbitraryBuildingOSMParser.class);
    final ExecutorService executor;
    private final CoordinateTransformation transformation;
    private Map<Long, BuildingData> buildingData;
    private Map<Long, Set<Long>> buildingDataNodeStorage;

    private Map<Long, Coord> nodeStorage;
    private HashMap<Id<Zone>, BuildingsPerZone> buildingsPerZone;


    ArbitraryBuildingOSMParser(CoordinateTransformation transformation, ExecutorService executor) {
        this.transformation = transformation;
        this.executor = executor;
    }

    public static void main(String[] args) {
        ArbitraryBuildingOSMParser arbitraryBuildingOSMParser = new ArbitraryBuildingOSMParser(TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, TransformationFactory.CH1903_LV03_Plus), Executors.newWorkStealingPool());
        arbitraryBuildingOSMParser.parse(Paths.get(args[0]));
        Zones zones = ZonesLoader.loadZones("ID_Zone", args[1], "ID_Zone");
        arbitraryBuildingOSMParser.assignZones(zones);
        arbitraryBuildingOSMParser.writeTable(args[2]);
        System.out.println("done");
    }

    public void writeTable(String outputFile) {
        String zoneId = "zoneId";
        String nuts3 = "NUTS3";
        String buildings = "buildings";
        String buildingArea = "buildingArea";
        String[] header = new String[]{zoneId, nuts3, buildings, buildingArea};
        try (CSVWriter writer = new CSVWriter(null, header, outputFile)) {
            for (var zoneData : this.buildingsPerZone.values()) {
                writer.set(zoneId, zoneData.zoneId().toString());
                writer.set(nuts3, zoneData.nuts3Id);
                writer.set(buildings, zoneData.buildings().toString());
                writer.set(buildingArea, zoneData.area().toString());
                writer.writeRow();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void assignZones(Zones zones) {
        GeometryFactory fac = new GeometryFactory();
        int errors = 0;
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
            Zone zone = null;
            try {
                 zone = zones.findZone(centroid);
            }
            catch (Exception e){
                errors++;
                e.printStackTrace();
            }

            if (zone != null) {
                var zoneId = zone.getId();
                String nuts3 = String.valueOf(zone.getAttribute("NUTS3"));
                double floors = data.floors()>0?data.floors():1.;
                buildingsPerZone.computeIfAbsent(zoneId, zoneId1 -> new BuildingsPerZone(zoneId1, nuts3, new MutableDouble(), new MutableInt())).add(polygon.getArea()*floors);

            }

        }
        System.out.println("Errors in Zone Mapping "+errors);
        System.out.println("Buildings: " + buildingData.size());


    }

    void parse(Path inputFile) {
        buildingData = new ConcurrentHashMap<>();
        buildingDataNodeStorage = new ConcurrentHashMap<>();
        nodeStorage = new ConcurrentHashMap<>();
        buildingsPerZone = new HashMap<>();
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

    public Map<String, WeightedRandomSelection> prepareRandomDistributor(boolean useBuildingArea, Random random) {
        Set<String> nuts3data = this.buildingsPerZone.values().stream().map(data -> data.nuts3Id()).collect(Collectors.toSet());
        Map<String,WeightedRandomSelection> weightsPerNuts = new HashMap<>();
        for (String nutsZone : nuts3data){
            WeightedRandomSelection<Id<Zone>> selection = new WeightedRandomSelection<>(random);
            this.buildingsPerZone.values().stream().filter(data-> data.nuts3Id.equals(nutsZone)).forEach(data->{
                double weight = useBuildingArea?data.area().doubleValue():data.buildings.doubleValue();
                selection.add(data.zoneId(),weight);


            });
            weightsPerNuts.put(nutsZone,selection);
        }
        return weightsPerNuts;
    }

    record BuildingData(long wayId, List<Long> nodeIds, int floors, boolean retail) {
    }

    record BuildingsPerZone(Id<Zone> zoneId, String nuts3Id, MutableDouble area, MutableInt buildings) {

        private void add(double area) {
            this.area.add(area);
            this.buildings.increment();
        }
    }


}
