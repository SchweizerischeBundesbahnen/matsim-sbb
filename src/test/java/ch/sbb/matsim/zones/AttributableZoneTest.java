package ch.sbb.matsim.zones;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * @author mrieser
 */
public class AttributableZoneTest {

	@Test
	public void testFindZones() {
		Zones zones = ZonesLoader.loadZones("testZones", "src/test/resources/shapefiles/AccessTime/accesstime_zone.shp", "ID");
		Assertions.assertEquals(5, zones.size());

		Zone z = zones.findZone(541588, 159160); // 5 / Lausanne
		Assertions.assertNotNull(z);
		Assertions.assertEquals("5", z.getId().toString());
		Assertions.assertEquals("Lausanne", z.getAttribute("NAME"));

		z = zones.findZone(600000, 200000); // 4 / Bern
		Assertions.assertNotNull(z);
		Assertions.assertEquals("4", z.getId().toString());
		Assertions.assertEquals("Bern", z.getAttribute("NAME"));
		Assertions.assertEquals(20, ((Integer) z.getAttribute("at_car")).intValue());

		z = zones.findZone(610000, 207000); // within bounding box of Bern, but outside the zone
		Assertions.assertNull(z);
	}
}