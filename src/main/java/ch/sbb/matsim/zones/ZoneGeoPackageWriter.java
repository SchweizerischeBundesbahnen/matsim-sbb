package ch.sbb.matsim.zones;

import ch.sbb.matsim.csv.CSVWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.feature.NameImpl;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.MultiPolygon;
import org.matsim.core.utils.gis.GeoFileWriter;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ZoneGeoPackageWriter {

    private static final Logger log = LogManager.getLogger(ZoneGeoPackageWriter.class);

    public static final String GEOMETRY = "geometry";
    public static final String SHAPE_FILE_GEOM_NAME = "the_geom";

    public static void writeGeoPackage(Zones zones, String outputFile) {
        writeGeoPackage(zones, outputFile, 2056); // Default EPSG code for CH1903+ / LV95
    }

    public static void writeGeoPackage(Zones zones, String outputFile, int epsg) {
        if (zones == null || zones.getZones() == null || zones.getZones().isEmpty()) {
            log.error("No zones to write to GeoPackage.");
            return;
        }
        File file = new File(outputFile);
        file.mkdirs();
        if (file.exists()) {
            log.warn("Output GeoPackage file already exists and will be overwritten: " + outputFile);
            file.delete();
        }

        List<AttributableZone> sfZones = new ArrayList<>();
        for (Zone zone : zones.getZones()) {
            if (zone instanceof AttributableZone sfz) {
                sfZones.add(sfz);
            }
        }

        if (sfZones.isEmpty()) {
            log.error("No AttributableZones to write to GeoPackage.");
            return;
        }

        try {
            Collection<SimpleFeature> features = buildFeaturesFromAttributes(sfZones, epsg, "zones");
            GeoFileWriter.writeGeometries(features, outputFile, new NameImpl("zones"));
        } catch (Exception e) {
            log.error("Error writing file: " + e.getMessage(), e);
        }
    }


    private static Collection<SimpleFeature> buildFeaturesFromAttributes(List<AttributableZone> zones, int epsg, String layerName) throws FactoryException {
        SimpleFeatureType featureType = createFeatureType(zones, epsg, layerName);
        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(featureType);
        List<SimpleFeature> features = new ArrayList<>();

        List<String> attributeNames = featureType.getAttributeDescriptors().stream()
                .map(descriptor -> descriptor.getLocalName())
                .filter(name -> !name.equals(GEOMETRY) && !name.equals(SHAPE_FILE_GEOM_NAME))
                .collect(Collectors.toList());

        for (AttributableZone zone : zones) {
            Geometry geometry = zone.getGeometry();
            geometry.setSRID(epsg);
            featureBuilder.set(GEOMETRY, geometry);
            for (String attr : attributeNames) {
                featureBuilder.set(attr, zone.getAttribute(attr));
            }
            SimpleFeature feature = featureBuilder.buildFeature(zone.getId().toString());
            features.add(feature);
        }
        return features;
    }

    private static SimpleFeatureType createFeatureType(List<AttributableZone> zones, int epsg, String layerName) throws FactoryException {
        SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
        typeBuilder.setName(layerName);
        typeBuilder.setDefaultGeometry(GEOMETRY);
        CoordinateReferenceSystem crs = CRS.decode("EPSG:" + epsg);
        typeBuilder.setCRS(crs);
        typeBuilder.add(GEOMETRY, MultiPolygon.class, crs);

        Map<String, Class<?>> attributeTypes = new LinkedHashMap<>();
        for (AttributableZone zone : zones) {
            for (Map.Entry<String, Object> entry : zone.getAttributes().getAsMap().entrySet()) {
                String attr = entry.getKey();
                Object val = entry.getValue();

                if (!attributeTypes.containsKey(attr) && !SHAPE_FILE_GEOM_NAME.equals(attr) && val != null) {
                    attributeTypes.put(attr, val.getClass());
                }
            }
        }

        for (Map.Entry<String, Class<?>> entry : attributeTypes.entrySet()) {
            String attr = entry.getKey();
            Class<?> type = entry.getValue();
            typeBuilder.add(attr, type != null ? type : String.class);
        }
        return typeBuilder.buildFeatureType();
    }


    public static void writeAttributesToCSV(Zones zones, String outputFile) {
        log.info("Writing attributes to CSV. " + outputFile);
        if (zones == null || zones.getZones() == null || zones.getZones().isEmpty()) {
            log.error("No zones to write to CSV.");
            return;
        }
        Set<String> attributeNames = new TreeSet<>();
        attributeNames.addAll(zones.getZones().stream().flatMap(zone -> zone.getAttributes().getAsMap().keySet().stream()).collect(Collectors.toSet()));
        attributeNames.remove("geometry"); // Exclude geometry attribute
        attributeNames.remove(SHAPE_FILE_GEOM_NAME); // Exclude geometry attribute
        attributeNames.remove("zone_id");
        List<String> columns = new ArrayList<>();
        columns.add("zone_id");
        columns.addAll(attributeNames);

        try (CSVWriter writer = new CSVWriter("\uFEFF", columns.toArray(String[]::new), outputFile)) {
            for (Zone zone : zones.getZones()) {
                AttributableZone attributableZone = (AttributableZone) zone;
                writer.set("zone_id", attributableZone.getId().toString());
                for (String attr : attributeNames) {
                    Object value = attributableZone.getAttribute(attr);
                    if (value != null) {
                        writer.set(attr, value.toString());
                    } else {
                        writer.set(attr, ""); // Set empty string for null values
                    }
                }
                writer.writeRow();
            }
        } catch (IOException e) {
            log.error("Error writing attributes to CSV: " + e.getMessage(), e);
        }
    }


}