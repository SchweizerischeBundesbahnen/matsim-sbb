package ch.sbb.matsim.zones;

import ch.sbb.matsim.config.ZonesListConfigGroup;
import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;

/**
 * @author mrieser
 */
public class ZonesLoaderTest {

	@Test
	public void testLoadZones() {
		Zones zones = ZonesLoader.loadZones("testZones", "src/test/resources/shapefiles/AccessTime/accesstime_zone.SHP", "ID");
		Assert.assertEquals("testZones", zones.getId().toString());
		Assert.assertEquals(5, zones.size());
	}

	@Test
	public void testLoadAllZones() {
		Config config = ConfigUtils.createConfig();
		ZonesListConfigGroup cfg = new ZonesListConfigGroup();
		cfg.addZones(new ZonesListConfigGroup.ZonesParameterSet("testZones", "src/test/resources/shapefiles/AccessTime/accesstime_zone.SHP", "ID"));
		ZonesCollection zonesCollection = new ZonesCollection();
		config.addModule(cfg);
		ZonesLoader.loadAllZones(config, zonesCollection);

		Zones testZones = zonesCollection.getZones(Id.create("testZones", Zones.class));
		Assert.assertNotNull(testZones);
		Assert.assertEquals(5, testZones.size());
	}
}