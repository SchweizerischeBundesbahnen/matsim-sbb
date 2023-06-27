package ch.sbb.matsim.zones;

import ch.sbb.matsim.config.ZonesListConfigGroup;
import ch.sbb.matsim.config.variables.Variables;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Id;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.utils.gis.ShapeFileReader;
import org.opengis.feature.simple.SimpleFeature;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Locale;

/**
 * Loads all zones specified in the configuration.
 *
 * @author mrieser
 */
public final class ZonesLoader {

	private ZonesLoader() {
	}

	public static void loadAllZones(Config config, ZonesCollection zonesCollection) {
		ZonesListConfigGroup zonesConfig = ConfigUtils.addOrGetModule(config, ZonesListConfigGroup.class);

		for (ZonesListConfigGroup.ZonesParameterSet group : zonesConfig.getZones()) {
			String id = group.getId();
			URL filenameURL = group.getFilenameURL(config.getContext());
			String filenameString = group.getFilename();
			String idAttribute = group.getIdAttributeName();
			Zones zones = loadZones(id, filenameURL, filenameString, idAttribute);
			zonesCollection.addZones(zones);
		}
    }

    public static Zones loadZones(String id, String filename, String idAttribute) {
		LogManager.getLogger(ZonesLoader.class).info(" zones file " + filename);
		if (filename.toLowerCase(Locale.ROOT).endsWith(".shp")) {
			return loadZonesFromShapefile(id, filename, idAttribute);
		}
		throw new RuntimeException("Unsupported format for zones-file " + filename);
	}

	public static Zones loadZones(String id, String filename) {
		return loadZones(id, filename, Variables.ZONE_ID);
	}

	public static Zones loadZones(String id, URL filenameURL, String fileNameString, String idAttribute) {
		try {
			String filename = new File(filenameURL.toURI()).getAbsolutePath();
			return loadZones(id, filename, idAttribute);
		} catch (URISyntaxException e) {
			return loadZones(id, fileNameString, idAttribute);
		}

	}

	private static Zones loadZonesFromShapefile(String id, String filename, String idAttribute) {
		boolean noZoneId = idAttribute == null || idAttribute.isEmpty();
		ZonesImpl zones = new ZonesImpl(Id.create(id, Zones.class));
		for (SimpleFeature sf : ShapeFileReader.getAllFeatures(filename)) {
			String zoneId = noZoneId ? null : sf.getAttribute(idAttribute).toString();
			Zone zone = new SimpleFeatureZone(noZoneId ? null : Id.create(zoneId, Zone.class), sf);
			zones.add(zone);
		}
		return zones;
	}
}
