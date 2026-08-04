package ch.sbb.matsim.zones;

import ch.sbb.matsim.csv.CSVWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.feature.simple.SimpleFeatureType;
import org.geotools.api.feature.type.AttributeDescriptor;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.feature.NameImpl;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.*;
import org.matsim.core.utils.gis.GeoFileWriter;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class ZoneGeoPackageWriter {

    private static final Logger log = LogManager.getLogger(ZoneGeoPackageWriter.class);

    public static final String GEOMETRY = "geometry";
    public static final String SHAPE_FILE_GEOM_NAME = "the_geom";
    // Attribute names that must be excluded when writing GeoPackage, as they conflict with internal GeoPackage columns
    private static final Set<String> EXCLUDED_ATTRIBUTES = Set.of(GEOMETRY, SHAPE_FILE_GEOM_NAME, "fid", "geom");

    public static void writeGeoPackage(Zones zones, String outputFile) {
        writeGeoPackage(zones, outputFile, 2056); // Default EPSG code for CH1903+ / LV95
    }

    public static void writeGeoPackage(Zones zones, String outputFile, int epsg) {
        if (zones == null || zones.getZones() == null || zones.getZones().isEmpty()) {
            log.error("No zones to write to GeoPackage.");
            return;
        }
        File file = new File(outputFile);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                log.warn("Could not create parent directories for: " + parent.getAbsolutePath());
            }
        }
        if (file.exists()) {
            log.warn("Output GeoPackage file already exists and will be overwritten: " + outputFile);
            if (!file.delete()) {
                log.warn("Could not delete existing file: " + outputFile);
            }
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
                .map(AttributeDescriptor::getLocalName)
                .filter(name -> !EXCLUDED_ATTRIBUTES.contains(name))
                .toList();

        for (AttributableZone zone : zones) {
            Geometry geometry = zone.getGeometry();
            if (geometry != null) {
                Geometry g = convertToMultiPolygonIfNeeded(geometry);
                g.setSRID(epsg);
                featureBuilder.reset();
                featureBuilder.set(GEOMETRY, g);
            } else {
                featureBuilder.reset();
                featureBuilder.set(GEOMETRY, null);
            }

            for (String attr : attributeNames) {
                AttributeDescriptor descriptor = featureType.getDescriptor(attr);
                Object val = zone.getAttribute(attr);
                Object normalized = normalizeValueForDescriptor(val, descriptor, attr);
                featureBuilder.set(attr, normalized);
            }
            // Use null as feature ID so GeoTools generates auto-increment integer FIDs
            // (GeoPackage requires integer primary keys for fid). Zone ID is stored as a regular attribute.
            SimpleFeature feature = featureBuilder.buildFeature(null);
            features.add(feature);
        }
        return features;
    }

    private static Geometry convertToMultiPolygonIfNeeded(Geometry geometry) {
        if (geometry == null) return null;
        try {
            switch (geometry) {
                case Polygon poly -> {
                    GeometryFactory gf = poly.getFactory();
                    return gf.createMultiPolygon(new Polygon[]{poly});
                }
                case MultiPolygon _ -> {
                    return geometry;
                }

                // If other geometry types appear, try to convert polygons inside (best-effort)
                case GeometryCollection _ -> {
                    List<Polygon> polys = new ArrayList<>();
                    for (int i = 0; i < geometry.getNumGeometries(); i++) {
                        Geometry g = geometry.getGeometryN(i);
                        if (g instanceof Polygon) polys.add((Polygon) g);
                    }
                    if (!polys.isEmpty()) {
                        return geometry.getFactory().createMultiPolygon(polys.toArray(new Polygon[0]));
                    }
                }
                default -> {
                }
            }
        } catch (Exception e) {
            log.debug("Error converting geometry to MultiPolygon: " + e.getMessage(), e);
        }
        return geometry;
    }

    private static Object normalizeValueForDescriptor(Object val, AttributeDescriptor descriptor, String attrName) {
        if (val == null) return null;
        if (descriptor == null) {
            // No descriptor known, fallback to toString
            log.debug("No descriptor for attribute '{}', using toString()", attrName);
            return val.toString();
        }
        Class<?> binding = descriptor.getType().getBinding();

        // handle numbers with NaN/Infinity check
        if (val instanceof Number) {
            double dv = ((Number) val).doubleValue();
            if (Double.isNaN(dv) || Double.isInfinite(dv)) {
                log.debug("Attribute '{}' value is NaN/Infinite, setting null", attrName);
                return null;
            }
        }

        if (Geometry.class.isAssignableFrom(binding)) {
            if (val instanceof Geometry) {
                return convertToMultiPolygonIfNeeded((Geometry) val);
            } else {
                log.debug("Attribute '{}' expected Geometry but got {} -> null", attrName, val.getClass().getSimpleName());
                return null;
            }
        }

        if (Number.class.isAssignableFrom(binding) || binding == int.class || binding == double.class || binding == long.class) {
            if (val instanceof Number n) {
                if (binding == Integer.class || binding == int.class) return n.intValue();
                if (binding == Long.class || binding == long.class) return n.longValue();
                if (binding == Double.class || binding == double.class) return n.doubleValue();
                return n;
            }
            if (val instanceof String) {
                String s = ((String) val).trim();
                if (s.isEmpty()) return null;
                try {
                    if (binding == Integer.class || binding == int.class) return Integer.parseInt(s);
                    if (binding == Long.class || binding == long.class) return Long.parseLong(s);
                    if (binding == Double.class || binding == double.class) return Double.parseDouble(s);
                    // fallback: parse as double
                    return Double.parseDouble(s);
                } catch (NumberFormatException e) {
                    log.debug("Could not parse attribute '{}' value '{}' to number for binding {} -> null", attrName, s, binding.getSimpleName());
                    return null;
                }
            }
            // unknown type for numeric binding
            log.debug("Attribute '{}' has numeric binding {} but value type {} -> null", attrName, binding.getSimpleName(), val.getClass().getSimpleName());
            return null;
        }

        if (binding == String.class) {
            return val.toString();
        }

        if (binding == Boolean.class || binding == boolean.class) {
            if (val instanceof Boolean) return val;
            String s = val.toString().trim().toLowerCase();
            switch (s) {
                case "" -> {
                    return null;
                }
                case "true", "1", "yes" -> {
                    return Boolean.TRUE;
                }
                case "false", "0", "no" -> {
                    return Boolean.FALSE;
                }
            }
            log.debug("Could not parse boolean for attribute '{}' value '{}' -> null", attrName, val);
            return null;
        }

        if (Date.class.isAssignableFrom(binding)) {
            if (val instanceof Date) return val;
            // optional: try parsing common date formats - omitted here for brevity
            log.debug("Attribute '{}' expected Date but got {} -> null", attrName, val.getClass().getSimpleName());
            return null;
        }

        // Fallback: if target is assignable from value, return it; else use toString for String bindings, else null
        if (binding.isInstance(val)) return val;

        log.debug("Fallback: attribute '{}' with binding {} cannot accept value of type {} -> using null", attrName, binding.getSimpleName(), val.getClass().getSimpleName());
        return null;
    }

    private static SimpleFeatureType createFeatureType(List<AttributableZone> zones, int epsg, String layerName) throws FactoryException {
        SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
        typeBuilder.setName(layerName);
        typeBuilder.setDefaultGeometry(GEOMETRY);
        CoordinateReferenceSystem crs = CRS.decode("EPSG:" + epsg);
        typeBuilder.setCRS(crs);
        typeBuilder.add(GEOMETRY, MultiPolygon.class, crs);

        // Sammle alle Werte pro Attribut (insertion order preserved)
        Map<String, List<Object>> valuesPerAttr = new LinkedHashMap<>();
        for (AttributableZone zone : zones) {
            Map<String, Object> map = zone.getAttributes().getAsMap();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String attr = entry.getKey();
                if (EXCLUDED_ATTRIBUTES.contains(attr)) continue;
                valuesPerAttr.computeIfAbsent(attr, _ -> new ArrayList<>()).add(entry.getValue());
            }
        }

        for (Map.Entry<String, List<Object>> e : valuesPerAttr.entrySet()) {
            String rawAttr = e.getKey();
            String attr = sanitizeAttributeName(rawAttr);
            Class<?> binding = determineBestBinding(e.getValue());
            typeBuilder.add(attr, binding != null ? binding : String.class);
            if (!attr.equals(rawAttr)) {
                log.debug("Sanitized attribute name '{}' -> '{}'", rawAttr, attr);
            }
            log.debug("Attribute '{}' binding determined as {}", attr, binding != null ? binding.getSimpleName() : "String");
        }

        return typeBuilder.buildFeatureType();
    }

    private static String sanitizeAttributeName(String name) {
        if (name == null) return "attr";
        String s = name.trim().replaceAll("[^A-Za-z0-9_]", "_");
        if (s.isEmpty()) s = "attr";
        if (s.length() > 60) s = s.substring(0, 60);
        return s;
    }

    private static Class<?> determineBestBinding(List<Object> values) {
        boolean anyString = false;
        boolean anyBoolean = false;
        boolean anyInteger = false;
        boolean anyLong = false;
        boolean anyDouble = false;
        boolean anyDate = false;
        boolean anyGeometry = false;

        for (Object v : values) {
            switch (v) {
                case null -> {
                    continue;
                }
                case Geometry _ -> {
                    anyGeometry = true;
                    continue;
                }
                case Integer _ -> {
                    anyInteger = true;
                    continue;
                }
                case Long _ -> {
                    anyLong = true;
                    continue;
                }
                default -> {
                }
            }
            if (v instanceof Double || v instanceof Float) { anyDouble = true; continue; }
            switch (v) {
                case Number _ -> anyDouble = true;
                case Date _ -> anyDate = true;
                case Boolean _ -> anyBoolean = true;
                case String string -> {
                    String s = string.trim();
                    if (s.isEmpty()) continue;
                    anyString = true;
                    if (isInteger(s)) {
                        anyInteger = true;
                        continue;
                    }
                    if (isLong(s)) {
                        anyLong = true;
                        continue;
                    }
                    if (isDouble(s)) {
                        anyDouble = true;
                        continue;
                    }
                    if (isBooleanString(s)) {
                        anyBoolean = true;
                    }
                    // could attempt date parse here if needed
                }
                default -> {
                }
            }
        }

        if (anyGeometry) return Geometry.class;
        if (anyDouble) return Double.class;
        if (anyLong) return Long.class;
        if (anyInteger) return Integer.class;
        if (anyBoolean) return Boolean.class;
        if (anyDate) return Date.class;
        if (anyString) return String.class;
        return String.class;
    }

    private static boolean isInteger(String s) {
        try { Integer.parseInt(s); return true; } catch (Exception ex) { return false; }
    }

    private static boolean isLong(String s) {
        try { Long.parseLong(s); return true; } catch (Exception ex) { return false; }
    }

    private static boolean isDouble(String s) {
        try { Double.parseDouble(s); return true; } catch (Exception ex) { return false; }
    }

    private static boolean isBooleanString(String s) {
        String low = s.toLowerCase();
        return "true".equals(low) || "false".equals(low) || "1".equals(low) || "0".equals(low) || "yes".equals(low) || "no".equals(low);
    }

    public static void writeAttributesToCSV(Zones zones, String outputFile) {
        log.info("Writing attributes to CSV. " + outputFile);
        if (zones == null || zones.getZones() == null || zones.getZones().isEmpty()) {
            log.error("No zones to write to CSV.");
            return;
        }
        Set<String> attributeNames = zones.getZones().stream().flatMap(zone -> zone.getAttributes().getAsMap().keySet().stream()).collect(Collectors.toCollection(TreeSet::new));
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
