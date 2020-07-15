package ch.sbb.matsim.config;

import java.io.ByteArrayInputStream;
import java.util.Iterator;
import org.junit.Assert;
import org.junit.Test;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigReader;
import org.matsim.core.config.ConfigUtils;

/**
 * @author mrieser
 */
public class ZonesListConfigGroupTest {

	static {
		System.setProperty("matsim.preferLocalDtds", "true");
	}

	@Test
	public void testFromXml_empty() {
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<!DOCTYPE config SYSTEM \"http://www.matsim.org/files/dtd/config_v2.dtd\">" +
				"<config></config>";

		ZonesListConfigGroup zonesCfg = new ZonesListConfigGroup();
		Config config = ConfigUtils.createConfig(zonesCfg);
		ConfigReader reader = new ConfigReader(config);
		reader.parse(new ByteArrayInputStream(xml.getBytes()));

		Assert.assertEquals(0, zonesCfg.getZones().size());
	}

	@Test
	public void testFromXml_missing() {
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<!DOCTYPE config SYSTEM \"http://www.matsim.org/files/dtd/config_v2.dtd\">" +
				"<config><module name=\"zones\"></module></config>";

		ZonesListConfigGroup zonesCfg = new ZonesListConfigGroup();
		Config config = ConfigUtils.createConfig(zonesCfg);
		ConfigReader reader = new ConfigReader(config);
		reader.parse(new ByteArrayInputStream(xml.getBytes()));

		Assert.assertEquals(0, zonesCfg.getZones().size());
	}

	@Test
	public void testFromXml_1paramset() {
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<!DOCTYPE config SYSTEM \"http://www.matsim.org/files/dtd/config_v2.dtd\">" +
				"<config><module name=\"zones\">" +
				"\t<parameterset type=\"zones\">" +
				"\t\t<param name=\"id\" value=\"firstZones\" />" +
				"\t\t<param name=\"filename\" value=\"zones/firstZones.shp\" />" +
				"\t\t<param name=\"idAttributeName\" value=\"ZID\" />" +
				"\t</parameterset>" +
				"</module></config>";

		ZonesListConfigGroup zonesCfg = new ZonesListConfigGroup();
		Config config = ConfigUtils.createConfig(zonesCfg);
		ConfigReader reader = new ConfigReader(config);
		reader.parse(new ByteArrayInputStream(xml.getBytes()));

		Assert.assertEquals(1, zonesCfg.getZones().size());

		ZonesListConfigGroup.ZonesParameterSet paramset1 = zonesCfg.getZones().iterator().next();
		Assert.assertEquals("firstZones", paramset1.getId());
		Assert.assertEquals("zones/firstZones.shp", paramset1.getFilename());
		Assert.assertEquals("ZID", paramset1.getIdAttributeName());
	}

	@Test
	public void testFromXml_2paramsets() {
		String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
				"<!DOCTYPE config SYSTEM \"http://www.matsim.org/files/dtd/config_v2.dtd\">" +
				"<config><module name=\"zones\">" +
				"\t<parameterset type=\"zones\">" +
				"\t\t<param name=\"id\" value=\"firstZones\" />" +
				"\t\t<param name=\"filename\" value=\"zones/firstZones.shp\" />" +
				"\t\t<param name=\"idAttributeName\" value=\"ZID\" />" +
				"\t</parameterset>" +
				"\t<parameterset type=\"zones\">" +
				"\t\t<param name=\"id\" value=\"secondZones\" />" +
				"\t\t<param name=\"filename\" value=\"zones/2ndZones.shp\" />" +
				"\t\t<param name=\"idAttributeName\" value=\"Z2ID\" />" +
				"\t</parameterset>" +
				"</module></config>";

		ZonesListConfigGroup zonesCfg = new ZonesListConfigGroup();
		Config config = ConfigUtils.createConfig(zonesCfg);
		ConfigReader reader = new ConfigReader(config);
		reader.parse(new ByteArrayInputStream(xml.getBytes()));

		Assert.assertEquals(2, zonesCfg.getZones().size());

		Iterator<ZonesListConfigGroup.ZonesParameterSet> iter = zonesCfg.getZones().iterator();
		ZonesListConfigGroup.ZonesParameterSet paramset1 = iter.next();
		Assert.assertEquals("firstZones", paramset1.getId());
		Assert.assertEquals("zones/firstZones.shp", paramset1.getFilename());
		Assert.assertEquals("ZID", paramset1.getIdAttributeName());

		ZonesListConfigGroup.ZonesParameterSet paramset2 = iter.next();
		Assert.assertEquals("secondZones", paramset2.getId());
		Assert.assertEquals("zones/2ndZones.shp", paramset2.getFilename());
		Assert.assertEquals("Z2ID", paramset2.getIdAttributeName());
	}

	@Test
	public void testFromCode() {
		ZonesListConfigGroup zonesCfg = new ZonesListConfigGroup();
		Assert.assertEquals(0, zonesCfg.getZones().size());

		zonesCfg.addZones(new ZonesListConfigGroup.ZonesParameterSet("firstZones", "zones/firstZones.shp", "ZID"));
		Assert.assertEquals(1, zonesCfg.getZones().size());
	}

}