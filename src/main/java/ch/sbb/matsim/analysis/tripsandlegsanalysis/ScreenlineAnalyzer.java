package ch.sbb.matsim.analysis.tripsandlegsanalysis;

import ch.sbb.matsim.analysis.linkAnalysis.CarLinkAnalysis.LinkStorage;
import ch.sbb.matsim.config.PostProcessingConfigGroup;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.*;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Singleton
public class ScreenlineAnalyzer {

    private static final Logger log = LogManager.getLogger(ScreenlineAnalyzer.class);
    private final GeometryFactory gf = new GeometryFactory();
    private final Network network;


    // Parsed screenline data
    private final Map<Integer, Screenline> screenlines = new LinkedHashMap<>();
    // Mapped links: LinkId -> List of CrossingInfo (a link can cross multiple screenlines)
    private final Map<Id<Link>, List<LinkCrossing>> linkCrossings = new HashMap<>();

    public record Screenline(int id, String name, LineString geometry) {}
    public record LinkCrossing(int screenlineId, String direction) {}

    @Inject
    public ScreenlineAnalyzer(Scenario scenario) {
        this.network = scenario.getNetwork();
        PostProcessingConfigGroup ppConfig = (PostProcessingConfigGroup) scenario.getConfig().getModules().get(PostProcessingConfigGroup.GROUP_NAME);
        initScreenlines(ppConfig.getScreenLinesFileURL(scenario.getConfig()));


    }

    /**
     * Initializes screenlines from the Visum .net text or file and intersects them with the network.
     */
    public void initScreenlines(URL screenlineNetFilePath) {

        if (screenlineNetFilePath != null) {
            loadScreenlinesFromGeoJson(screenlineNetFilePath);
            matchLinksToScreenlines();
        }
        else {
            log.error("Screenline file is null. Not processing any screenlines. Make sure to set it in PostProcessingConfigGroup");
        }
    }

    /**
     * Loads screenlines from a GeoJSON file (FeatureCollection of LineStrings).
     * Supports standard properties ("id" / "ID", "name" / "NAME").
     */
    private void loadScreenlinesFromGeoJson(URL geoJsonPath) {
        screenlines.clear();
        ObjectMapper mapper = new ObjectMapper();

        try {
            JsonNode root = mapper.readTree(geoJsonPath);
            JsonNode features = root.path("features");

            if (!features.isArray()) {
                log.error("Invalid GeoJSON: 'features' array not found in " + geoJsonPath);
                return;
            }

            for (JsonNode feature : features) {
                JsonNode properties = feature.path("properties");
                JsonNode geometry = feature.path("geometry");

                // 1. Extract ID (lenient lookup)
                int id = -1;
                if (properties.has("id")) {
                    id = properties.get("id").asInt();
                } else if (properties.has("ID")) {
                    id = properties.get("ID").asInt();
                } else if (properties.has("screenline_id")) {
                    id = properties.get("screenline_id").asInt();
                } else if (feature.has("id")) {
                    id = feature.get("id").asInt();
                }

                if (id == -1) {
                    log.warn("Skipping feature without a valid 'id' property in " + geoJsonPath);
                    continue;
                }

                // 2. Extract Name
                String name = "SL_" + id;
                if (properties.has("name")) {
                    name = properties.get("name").asText();
                } else if (properties.has("NAME")) {
                    name = properties.get("NAME").asText();
                }

                // 3. Extract LineString coordinates
                String geomType = geometry.path("type").asText();
                if (!"LineString".equalsIgnoreCase(geomType)) {
                    log.warn("Skipping screenline " + id + " (" + name + "): Geometry is " + geomType + ", expected LineString.");
                    continue;
                }

                JsonNode coordinatesNode = geometry.path("coordinates");
                if (!coordinatesNode.isArray() || coordinatesNode.size() < 2) {
                    log.warn("Skipping screenline " + id + ": Needs at least 2 coordinate pairs.");
                    continue;
                }

                List<Coordinate> coords = new ArrayList<>();
                for (JsonNode coordPair : coordinatesNode) {
                    if (coordPair.isArray() && coordPair.size() >= 2) {
                        double x = coordPair.get(0).asDouble();
                        double y = coordPair.get(1).asDouble();
                        coords.add(new Coordinate(x, y));
                    }
                }

                if (coords.size() >= 2) {
                    LineString lineString = gf.createLineString(coords.toArray(new Coordinate[0]));
                    screenlines.put(id, new Screenline(id, name, lineString));
                }
            }

            log.info("Successfully loaded " + screenlines.size() + " screenlines from " + geoJsonPath);

        } catch (IOException e) {
            log.error("Failed to read GeoJSON file: " + geoJsonPath, e);
        }
    }

    private void matchLinksToScreenlines() {
        linkCrossings.clear();
        for (Link link : network.getLinks().values()) {
            Coordinate from = new Coordinate(link.getFromNode().getCoord().getX(), link.getFromNode().getCoord().getY());
            Coordinate to = new Coordinate(link.getToNode().getCoord().getX(), link.getToNode().getCoord().getY());
            LineString linkGeom = gf.createLineString(new Coordinate[]{from, to});

            for (Screenline sl : screenlines.values()) {
                if (sl.geometry().intersects(linkGeom)) {
                    // Compute crossing direction (2D cross product: Screenline vector vs Link vector)
                    Coordinate slStart = sl.geometry().getCoordinateN(0);
                    Coordinate slEnd = sl.geometry().getCoordinateN(sl.geometry().getNumPoints() - 1);
                    double dxSl = slEnd.x - slStart.x;
                    double dySl = slEnd.y - slStart.y;
                    double dxLink = to.x - from.x;
                    double dyLink = to.y - from.y;

                    // > 0 = Dir_Forward (Left-to-Right), < 0 = Dir_Backward (Right-to-Left)
                    String direction = (dxSl * dyLink - dySl * dxLink >= 0) ? "FORWARD" : "BACKWARD";
                    linkCrossings.computeIfAbsent(link.getId(), k -> new ArrayList<>())
                                 .add(new LinkCrossing(sl.id(), direction));
                }
            }
        }
        log.info("Found " + linkCrossings.size() + " links crossing screenlines.");
    }

    /**
     * Aggregates both Car and PT link volumes and writes out the screenlines CSV.
     */
    public void writeScreenlineResults(String outputFile, double scaleFactor,  Map<Id<Link>, LinkStorage> streetVolumes,Map<Id<Link>, Long> ptVolumes ) {

        // Aggregator: Key = "SL_ID;SL_NAME;DIRECTION", Value = Mode counts
        Map<String, double[]> slAgg = new LinkedHashMap<>();

        // Indices: 0: CAR, 1: RIDE, 2: BIKE, 3: EBIKE, 4: FREIGHT, 5: PT, 6: TOTAL
        for (Map.Entry<Id<Link>, List<LinkCrossing>> entry : linkCrossings.entrySet()) {
            Id<Link> linkId = entry.getKey();
            List<LinkCrossing> crossings = entry.getValue();

            LinkStorage street = streetVolumes.get(linkId);
            Long pt = ptVolumes.get(linkId);

            double car = (street != null ? street.getCarCount() : 0) * scaleFactor;
            double ride = (street != null ? street.getRideCount() : 0) * scaleFactor;
            double bike = (street != null ? street.getBikeCount() : 0) * scaleFactor;
            double ebike = (street != null ? street.getEbikeCount() : 0) * scaleFactor;
            double freight = (street != null ? street.getFreightCount() : 0) * scaleFactor;
            double ptPax = (pt != null ? pt : 0) * scaleFactor;

            for (LinkCrossing crossing : crossings) {
                Screenline sl = screenlines.get(crossing.screenlineId());
                String key = sl.id() + ";" + sl.name() + ";" + crossing.direction();

                double[] counts = slAgg.computeIfAbsent(key, k -> new double[7]);
                counts[0] += car;
                counts[1] += ride;
                counts[2] += bike;
                counts[3] += ebike;
                counts[4] += freight;
                counts[5] += ptPax;
                counts[6] += (car + ride + bike + ebike + freight + ptPax);
            }
        }

        // Write CSV
        try (BufferedWriter w = new BufferedWriter(new FileWriter(outputFile, StandardCharsets.UTF_8))) {
            w.write("SCREENLINE_ID;SCREENLINE_NAME;DIRECTION;CAR;RIDE;BIKE;EBIKE;FREIGHT;PT;TOTAL\n");
            for (Map.Entry<String, double[]> e : slAgg.entrySet()) {
                double[] c = e.getValue();
                w.write(String.format(Locale.US, "%s;%.0f;%.0f;%.0f;%.0f;%.0f;%.0f;%.0f\n",
                        e.getKey(), c[0], c[1], c[2], c[3], c[4], c[5], c[6]));
            }
        } catch (IOException e) {
            log.error("Could not write screenline CSV to " + outputFile, e);
        }
    }
}