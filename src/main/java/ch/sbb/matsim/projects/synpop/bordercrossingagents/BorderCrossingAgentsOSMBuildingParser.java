package ch.sbb.matsim.projects.synpop.bordercrossingagents;

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
import org.matsim.core.gbl.MatsimRandom;
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

public class BorderCrossingAgentsOSMBuildingParser {

    private static final Logger log = LogManager.getLogger(BorderCrossingAgentsOSMBuildingParser.class);
    final ExecutorService executor;
    private final CoordinateTransformation transformation;
    public final static List<String> RESIDENTIAL_TAGS = List.of("apartments", "barracks", " bungalow", "cabin", "detached", "dormitory", "farm", "ger", "house", "houseboat", "residential", "semdetached_house", "static_caravan", "stilt_house", "terrace", "yes");
    // building = yes is used very often for residential buildings.

    private Map<Long, BuildingData> buildingData;
    private Map<Long, Set<Long>> buildingDataNodeStorage;
    private final Zones zones;
    private final Map<Id<Zone>, WeightedRandomSelection<Long>> weightedBuildingsPerZone = new HashMap<>();

    private Map<Long, Coord> nodeStorage;
    private HashMap<Id<Zone>, BuildingsPerZone> buildingsPerZone;


    BorderCrossingAgentsOSMBuildingParser(CoordinateTransformation transformation, ExecutorService executor, Zones zones) {
        this.transformation = transformation;
        this.executor = executor;
        this.zones = zones;
    }

    public static void main(String[] args) {
        Zones zones = ZonesLoader.loadZones("ID_Zone", args[1], "ID_Zone");
        BorderCrossingAgentsOSMBuildingParser borderCrossingAgentsOSMBuildingParser = new BorderCrossingAgentsOSMBuildingParser(TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, TransformationFactory.CH1903_LV03_Plus), Executors.newWorkStealingPool(), zones);
        borderCrossingAgentsOSMBuildingParser.parse(Paths.get(args[0]));
        borderCrossingAgentsOSMBuildingParser.assignZones(MatsimRandom.getRandom());
        borderCrossingAgentsOSMBuildingParser.writeTable(args[2]);
        System.out.println("done");
    }

    public Map<Long, BuildingData> getBuildingData() {
        return buildingData;
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

    public void assignZones(Random random) {

        GeometryFactory fac = new GeometryFactory();
        int errors = 0;
        int buildingErrors = 0;
        for (var data : buildingData.values()) {
            Zone zone = null;
            Polygon polygon = null;
            try {
            Coordinate[] coords = new Coordinate[data.nodeIds.size() + 1];
            for (int i = 0; i < data.nodeIds.size(); i++) {
                long nodeid = data.nodeIds().get(i);
                Coord coord = nodeStorage.get(nodeid);
                if (coord == null) {
                    buildingErrors++;
                }
                coords[i] = new Coordinate(coord.getX(), coord.getY());
            }
            coords[data.nodeIds.size()] = coords[0];
                polygon = fac.createPolygon(coords);
            Coord centroid = MGC.point2Coord(polygon.getCentroid());
            data.setCentroid(centroid);
                 zone = zones.findZone(centroid);
            }
            catch (Exception e){
                errors++;
                e.printStackTrace();
            }

            if (zone != null) {
                var zoneId = zone.getId();
                String nuts3 = String.valueOf(zone.getAttribute("NUTS3"));
                double floors = data.floors() > 0 ? data.floors() : 1.;
                if (data.residential) {
                    double weight = polygon.getArea() * floors;
                    buildingsPerZone.computeIfAbsent(zoneId, zoneId1 -> new BuildingsPerZone(zoneId1, nuts3, new MutableDouble(), new MutableInt())).add(weight);
                    weightedBuildingsPerZone.computeIfAbsent(zoneId, zoneId1 -> new WeightedRandomSelection<>(random)).add(data.wayId, weight);
                }

            }

        }
        System.out.println("Errors in Zone Mapping "+errors);
        System.out.println("Errors in Building Mapping " + buildingErrors);
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
            boolean isResidential = RESIDENTIAL_TAGS.contains(tags.get("building"));
            String levelString = tags.get("building:levels");
            int levels = -1;
            if (levelString != null) {
                try {
                    levels = Integer.parseInt(levelString);
                } catch (NumberFormatException e) {
                }
            }
            buildingData.put(osmWay.getId(), new BuildingData(osmWay.getId(), nodeIds, levels, isResidential));
            nodeIds.forEach(nodeId -> buildingDataNodeStorage.computeIfAbsent(nodeId, a -> new HashSet<>()).add(osmWay.getId()));

        }


    }

    public Map<Id<Zone>, WeightedRandomSelection<Long>> getWeightedBuildingsPerZone() {
        return weightedBuildingsPerZone;
    }

    public Map<String, WeightedRandomSelection<Id<Zone>>> prepareRandomDistributor(boolean useBuildingArea, Random random) {
        Set<String> nuts3data = this.buildingsPerZone.values().stream().map(data -> data.nuts3Id()).collect(Collectors.toSet());
        Map<String, WeightedRandomSelection<Id<Zone>>> weightsPerNuts = new HashMap<>();
        for (String nutsZone : nuts3data) {
            WeightedRandomSelection<Id<Zone>> selection = new WeightedRandomSelection<>(random);
            this.buildingsPerZone.values().stream().filter(data -> data.nuts3Id.equals(nutsZone)).forEach(data -> {
                Zone zone = zones.getZone(data.zoneId);
                double weight = (useBuildingArea ? data.area().doubleValue() : data.buildings.doubleValue()) / zone.getEnvelope().getArea() * getZonalCorrectionFactor(data.zoneId);

                selection.add(data.zoneId(), weight);


            });
            weightsPerNuts.put(nutsZone, selection);
        }
        return weightsPerNuts;
    }

    private double getZonalCorrectionFactor(Id<Zone> zoneId) {
        double factor = 1.0;
        String zoneIdString = zoneId.toString();
        if (zoneIdString.equals(("730101001"))) {
            //Campione, very densely built and too attractive
            factor = 0.3;
        }
        if (zoneIdString.equals(("710101001"))) {
            //Buesingen am Hochrhein, high share of commuters
            factor = 3.0;
        }
        List<String> annemasseZones = List.of("837108103",
                "837108092",
                "837108091",
                "837108089",
                "837108098",
                "837108102",
                "837108095",
                "837108104",
                "837108109",
                "837108107",
                "837108100",
                "837108090",
                "837108085",
                "837108086");
        if (annemasseZones.contains(zoneIdString)) {
            factor = 0.25;
        }
        List<String> geneveTooUnatractiveZones = List.of("837108004", "837108151", "837108155", "837101004", "837108142", "837108121");
        if (geneveTooUnatractiveZones.contains(zoneIdString)) {
            factor = 7.0;
        }




        return factor;
    }

    static final class BuildingData {
        private final long wayId;
        private final List<Long> nodeIds;
        private final int floors;
        private final boolean residential;
        private Coord centroid;

        BuildingData(long wayId, List<Long> nodeIds, int floors, boolean residential) {
            this.wayId = wayId;
            this.nodeIds = nodeIds;
            this.floors = floors;
            this.residential = residential;
        }

        public void setCentroid(Coord centroid) {
            this.centroid = centroid;
        }

        public long wayId() {
            return wayId;
        }

        public List<Long> nodeIds() {
            return nodeIds;
        }

        public int floors() {
            return floors;
        }

        public boolean residential() {
            return residential;
        }

        public Coord centroid() {
            return centroid;
        }


    }

    record BuildingsPerZone(Id<Zone> zoneId, String nuts3Id, MutableDouble area, MutableInt buildings) {

        private void add(double area) {
            this.area.add(area);
            this.buildings.increment();
        }
    }


}
