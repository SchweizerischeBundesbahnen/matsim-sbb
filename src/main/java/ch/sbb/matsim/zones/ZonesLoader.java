package ch.sbb.matsim.zones;

import ch.sbb.matsim.config.ZonesListConfigGroup;
import org.matsim.api.core.v01.Id;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.opengis.feature.simple.SimpleFeature;

import java.util.Locale;

/**
 * Loads all zones specified in the configuration.
 *
 * @author mrieser
 */
public final class ZonesLoader {

    private ZonesLoader() {
    }

    public static void loadAllZones(ZonesListConfigGroup config, ZonesCollections zonesCollections) {
        for (ZonesListConfigGroup.ZonesParameterSet group : config.getZones()) {
            String id = group.getId();
            String filename = group.getFilename();
            String idAttribute = group.getIdAttributeName();
            Zones zones = loadZones(id, filename, idAttribute);
            zonesCollections.addZones(zones);
        }
    }

    public static Zones loadZones(String id, String filename, String idAttribute) {
        if (filename.toLowerCase(Locale.ROOT).endsWith(".shp")) {
            return loadZonesFromShapefile(id, filename, idAttribute);
        }
        throw new RuntimeException("Unsupported format for zones-file " + filename);
    }

    private static Zones loadZonesFromShapefile(String id, String filename, String idAttribute) {
        Zones zones = new Zones(Id.create(id, Zones.class));
        for (SimpleFeature sf : ShapeFileReader.getAllFeatures(filename)) {
            String zoneId = sf.getAttribute(idAttribute).toString();
            Zone zone = new SimpleFeatureZone(Id.create(zoneId, Zone.class), sf);
            zones.add(zone);
        }
        return zones;
    }
}
