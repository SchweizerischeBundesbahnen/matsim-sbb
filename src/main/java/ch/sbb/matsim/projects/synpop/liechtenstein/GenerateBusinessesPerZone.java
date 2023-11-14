package ch.sbb.matsim.projects.synpop.liechtenstein;


import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.csv.CSVWriter;
import ch.sbb.matsim.projects.synpop.OSMRetailParser;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import de.topobyte.osm4j.core.model.iface.OsmNode;
import de.topobyte.osm4j.core.model.iface.OsmWay;
import de.topobyte.osm4j.core.model.util.OsmModelUtil;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.GeometryFactory;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
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

/*
    A generic class to use OSM building Data as the source for businesses
 */
public class GenerateBusinessesPerZone {


    private final Map<Id<Zone>, JobsPerSectorAndZone> jobsPerSectorAndZoneMap = new HashMap<>();
    private final Map<String, String> osmBuildingTypeToSectorMap = new HashMap<>();
    private final Map<String, Integer> osmBuildingTypeToNOGAMap = new HashMap<>();
    private final Zones zones;
    private static final Logger log = LogManager.getLogger(GenerateBusinessesPerZone.class);
    final ExecutorService executor = Executors.newWorkStealingPool();
    private final CoordinateTransformation transformation = TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, TransformationFactory.CH1903_LV03_Plus);
    private final Map<Id<Zone>, Map<String, List<Business>>> zonalSectorBusinessSelector;
    private final Random random;
    private Map<Long, Set<Long>> buildingDataNodeStorage;
    private Map<Long, Coord> nodeStorage;
    private Map<Long, OSDMBuildingData> buildingData;


    public GenerateBusinessesPerZone(String osmFile, String osmBuildingTypeMapping, String jobsFile, Zones zones, Random random) {
        this.zones = zones;
        this.random = random;
        parseJobsData(jobsFile);
        parseOSMTypeMapping(osmBuildingTypeMapping);
        zonalSectorBusinessSelector = new HashMap<>();
        Set<String> sectors = new HashSet<>(osmBuildingTypeToSectorMap.values());
        for (var zoneId : jobsPerSectorAndZoneMap.keySet()) {
            Map<String, List<Business>> selectorPerZone = new HashMap<>();
            for (String sector : sectors) {
                selectorPerZone.put(sector, new ArrayList<>());
            }
            zonalSectorBusinessSelector.put(zoneId, selectorPerZone);
        }

        parse(Paths.get(osmFile));
        generateBusinesses();
        assignJobs();
    }

    public static void main(String[] args) {
        String osmFile = args[0];
        String osmBuildingTypeMapping = args[1];
        String jobsFile = args[2];
        String zonesFile = args[3];
        String outputBusinessesFile = args[4];
        var zones = ZonesLoader.loadZones("zones", zonesFile, "ID_Zone");
        Random random = MatsimRandom.getRandom();
        GenerateBusinessesPerZone businessesPerZone = new GenerateBusinessesPerZone(osmFile, osmBuildingTypeMapping, jobsFile, zones, random);
        businessesPerZone.writeBusinesses(outputBusinessesFile);
    }

    private void generateBusinesses() {
        GeometryFactory fac = new GeometryFactory();

        for (OSDMBuildingData data : this.buildingData.values()) {
            List<Long> nodeIds = data.nodeIds;
            var polygon = OSMRetailParser.getPolygon(fac, nodeIds, nodeStorage);
            Coord center = MGC.point2Coord(polygon.getCentroid());
            int floors = Math.max(1, data.floors);
            double area = polygon.getArea() * floors;
            double weight = data.type.equals("school") ? area * 0.1 : area;
            Zone zone = this.zones.findZone(center);
            if (zone != null) {
                var zoneId = zone.getId();
                String sector = osmBuildingTypeToSectorMap.get(data.type);
                Business business = new Business(center, zoneId, data.wayId, sector, data.type, weight, new MutableInt(), new MutableInt());
                zonalSectorBusinessSelector.get(zoneId).get(sector).add(business);
            }


        }

    }

    private void assignJobs() {
        for (var zonalJobs : jobsPerSectorAndZoneMap.values()) {
            Map<String, List<Business>> businessesPerSector = this.zonalSectorBusinessSelector.get(zonalJobs.zoneId);
            for (var jobsPerSector : zonalJobs.jobsPerSector().entrySet()) {
                String sector = jobsPerSector.getKey();
                int jobs = jobsPerSector.getValue();
                List<Business> businesses = businessesPerSector.get(sector);
                double totalAreaPerSector = businesses.stream().mapToDouble(business -> business.weight()).sum();
                businesses.forEach(business -> {
                    double share = business.weight / totalAreaPerSector;
                    double jobsTotal = share * jobs;
                    int jobsExo = (int) Math.round(zonalJobs.exoShare * jobsTotal);
                    int jobsEndo = (int) Math.round(jobsTotal - jobsExo);
                    business.jobsEndo.setValue(jobsEndo);
                    business.jobsExo.setValue(jobsExo);
                });
            }

        }
    }

    private void writeBusinesses(String outputBusinessesFile) {
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
        String osmWay = "osm_way";
        String osmType = "osm_type";
        List<Business> businesses = this.zonalSectorBusinessSelector.values().stream().flatMap(map -> map.values().stream().flatMap(list -> list.stream())).collect(Collectors.toList());

        try (CSVWriter writer = new CSVWriter(null, new String[]{business_id, zoneId, sector, noga, school_type, jobs_endo, fte_endo, jobs_exo, fte_exo, x, y, osmWay, osmType}, outputBusinessesFile)) {
            int businessId = 9_000_000;
            for (var business : businesses) {
                if (business.jobsEndo.intValue() > 0) {
                    Coord coord = business.coord;
                    writer.set(business_id, String.valueOf(businessId));
                    writer.set(osmWay, String.valueOf(business.osmWayId));
                    writer.set(sector, business.sector);
                    writer.set(noga, String.valueOf(osmBuildingTypeToNOGAMap.get(business.osmType())));
                    writer.set(school_type, drawSchoolType(business.osmType));
                    writer.set(jobs_endo, business.jobsEndo().toString());
                    writer.set(fte_endo, String.valueOf((int) business.jobsEndo().doubleValue() * 0.8));
                    writer.set(jobs_exo, business.jobsExo().toString());
                    writer.set(fte_exo, String.valueOf((int) business.jobsExo().doubleValue() * 0.8));
                    writer.set(x, String.valueOf(coord.getX()));
                    writer.set(y, String.valueOf(coord.getY()));
                    writer.set(zoneId, business.zoneId().toString());
                    writer.set(osmType, business.osmType);
                    writer.writeRow();
                    businessId++;
                }
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private String drawSchoolType(String type) {
        //mapping osm to school type is difficult. Can be manually adjusted if required.
        switch (type) {
            case "university" -> {
                return "other";
            }
            case "school" -> {
                double r = random.nextDouble();
                if (r < 0.1) return "other";
                if (r < 0.3) return "secondary_2";
                if (r < 0.5) return "secondary_1";
                else return "primary";
            }
            default -> {
                return "no_school";
            }
        }


    }

    private void parseOSMTypeMapping(String osmBuildingTypeMapping) {
        try (CSVReader reader = new CSVReader(osmBuildingTypeMapping, ";")) {
            var line = reader.readLine();
            while (line != null) {
                String osmType = line.get("type");
                String sector = line.get("sector");
                int noga = Integer.parseInt(line.get("noga"));
                osmBuildingTypeToSectorMap.put(osmType, sector);
                osmBuildingTypeToNOGAMap.put(osmType, noga);
                line = reader.readLine();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void parseJobsData(String jobsFile) {
        try (CSVReader reader = new CSVReader(jobsFile, ";")) {
            var line = reader.readLine();
            while (line != null) {
                Id<Zone> zoneId = Id.create(line.get("zone_id"), Zone.class);
                int agriculture = Integer.parseInt(line.get("agriculture"));
                int industry = Integer.parseInt(line.get("industry"));
                int services = Integer.parseInt(line.get("services"));
                double exoShare = Double.parseDouble(line.get("exo_share"));
                JobsPerSectorAndZone jobsPerSectorAndZone = new JobsPerSectorAndZone(zoneId, Map.of("agriculture", agriculture, "industry", industry, "services", services), exoShare);
                jobsPerSectorAndZoneMap.put(zoneId, jobsPerSectorAndZone);
                line = reader.readLine();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }


    void parse(Path inputFile) {
        buildingData = new ConcurrentHashMap<>();
        buildingDataNodeStorage = new ConcurrentHashMap<>();
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
        if (buildingDataNodeStorage.containsKey(osmNode.getId())) {
            nodeStorage.put(osmNode.getId(), transformation.transform(new Coord(osmNode.getLongitude(), osmNode.getLatitude())));
        }

    }

    void handleWay(OsmWay osmWay) {
        Map<String, String> tags = OsmModelUtil.getTagsAsMap(osmWay);
        if (tags.containsKey("building")) {
            List<Long> nodeIds = new ArrayList<>();
            OsmModelUtil.nodesAsList(osmWay).forEach(t -> nodeIds.add(t));
            String type = tags.get("building");
            if (osmBuildingTypeToNOGAMap.containsKey(type)) {
                String levelString = tags.get("building:levels");
                int levels = -1;
                if (levelString != null) {
                    try {
                        levels = Integer.parseInt(levelString);
                    } catch (NumberFormatException e) {
                    }
                }
                buildingData.put(osmWay.getId(), new OSDMBuildingData(osmWay.getId(), nodeIds, levels, type));
                nodeIds.forEach(nodeId -> buildingDataNodeStorage.computeIfAbsent(nodeId, a -> new HashSet<>()).add(osmWay.getId()));

            }
        }


    }

    record OSDMBuildingData(long wayId, List<Long> nodeIds, int floors, String type) {
    }

    record Business(Coord coord, Id<Zone> zoneId, long osmWayId, String sector, String osmType, double weight,
                    MutableInt jobsEndo, MutableInt jobsExo) {
    }

    record JobsPerSectorAndZone(Id<Zone> zoneId, Map<String, Integer> jobsPerSector, double exoShare) {
    }
}
