package ch.sbb.matsim.zones;

import ch.sbb.matsim.csv.CSVWriter;
import org.apache.logging.log4j.LogManager;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geopkg.FeatureEntry;
import org.geotools.geopkg.GeoPackage;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.MultiPolygon;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ZoneGeoPackageWriter {

    public static final String GEOMETRY = "geometry";

    public static void writeGeoPackage(Zones zones, String outputFile) throws Exception {
        writeGeoPackage(zones, outputFile, 2056); // Default EPSG code for CH1903+ / LV95
    }

    public static void writeGeoPackage(Zones zones, String outputFile, int epsg) throws Exception {
        if (zones == null || zones.getZones() == null || zones.getZones().isEmpty()) {
            LogManager.getLogger(ZoneGeoPackageWriter.class).error("No zones to write to GeoPackage.");
        }
        List<AttributableZone> sfZones = new ArrayList<>();
        for (Zone zone : zones.getZones()) {
            if (zone instanceof AttributableZone sfz) {
                sfZones.add(sfz);
            }
        }
        File file = new File(outputFile);
        String zonesName = file.getName().substring(0, file.getName().lastIndexOf('.'));
        System.out.println(zonesName);
        DefaultFeatureCollection defaultFeatureCollection = buildFeatureCollectionFromAttributes(sfZones, epsg, zonesName);
        try (GeoPackage geoPackage = new GeoPackage(file)) {
            geoPackage.init();
            FeatureEntry featureEntry = new FeatureEntry();
            featureEntry.setTableName(zonesName);
            featureEntry.setSrid(epsg);
            featureEntry.setGeometryColumn(GEOMETRY);
            geoPackage.add(featureEntry, defaultFeatureCollection);
        }
    }


    public static DefaultFeatureCollection buildFeatureCollectionFromAttributes(List<AttributableZone> zones, int epsg, String zonesName) throws FactoryException {
        SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
        typeBuilder.setName(zonesName);
        typeBuilder.add(GEOMETRY, MultiPolygon.class);
        typeBuilder.setDefaultGeometry(GEOMETRY);
        CoordinateReferenceSystem crs = CRS.decode("EPSG:" + epsg);
        typeBuilder.setCRS(crs);
        Map<String, Class<?>> attributeTypes = new LinkedHashMap<>();
        for (AttributableZone zone : zones) {
            for (Map.Entry<String, Object> entry : zone.getAttributes().getAsMap().entrySet()) {
                String attr = entry.getKey();
                Object val = entry.getValue();

                if (!attributeTypes.containsKey(attr) && val != null) {
                    attributeTypes.put(attr, val.getClass());
                }
            }
        }

        for (Map.Entry<String, Class<?>> entry : attributeTypes.entrySet()) {
            String attr = entry.getKey();
            Class<?> type = entry.getValue();
            typeBuilder.add(attr, type != null ? type : String.class);
        }
        SimpleFeatureType featureType = typeBuilder.buildFeatureType();
        SimpleFeatureBuilder featureBuilder = new SimpleFeatureBuilder(featureType);
        DefaultFeatureCollection features = new DefaultFeatureCollection();
        for (AttributableZone zone : zones) {
            featureBuilder.set(GEOMETRY, zone.getGeometry());
            for (String attr : attributeTypes.keySet()) {
                if (attr.equals(GEOMETRY)) {
                    continue; // Skip geometry attribute, already set
                }
                featureBuilder.set(attr, zone.getAttribute(attr));

            }
            SimpleFeature feature = featureBuilder.buildFeature(zone.getId().toString());
            features.add(feature);
        }
        return features;
    }


    public static void writeAttributesToCSV(Zones zones, String outputFile) {
        LogManager.getLogger(ZoneGeoPackageWriter.class).info("Writing attributes to CSV. " + outputFile);
        if (zones == null || zones.getZones() == null || zones.getZones().isEmpty()) {
            LogManager.getLogger(ZoneGeoPackageWriter.class).error("No zones to write to CSV.");
            return;
        }
        Set<String> attributeNames = new TreeSet<>();
        attributeNames.addAll(zones.getZones().stream().flatMap(zone -> zone.getAttributes().getAsMap().keySet().stream()).collect(Collectors.toSet()));
        attributeNames.remove("geometry"); // Exclude geometry attribute
        attributeNames.remove("the_geom"); // Exclude geometry attribute
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
            LogManager.getLogger(ZoneGeoPackageWriter.class).error("Error writing attributes to CSV: " + e.getMessage(), e);
        }
    }


}
