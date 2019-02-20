package ch.sbb.matsim.zones;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author mrieser
 */
public class SimpleFeatureZoneTest {

    @Test
    public void testFindZones() {
        Zones zones = ZonesLoader.loadZones("testZones", "src/test/resources/shapefiles/AccessTime/accesstime_zone.SHP", "ID");
        Assert.assertEquals(5, zones.size());

        Zone z = zones.findZone(541588, 159160); // 5 / Lausanne
        Assert.assertNotNull(z);
        Assert.assertEquals("5", z.getId().toString());
        Assert.assertEquals("Lausanne", z.getAttribute("NAME"));

        z = zones.findZone(600000, 200000); // 4 / Bern
        Assert.assertNotNull(z);
        Assert.assertEquals("4", z.getId().toString());
        Assert.assertEquals("Bern", z.getAttribute("NAME"));
        Assert.assertEquals(20, ((Integer) z.getAttribute("ACCCAR")).intValue());

        z = zones.findZone(610000, 207000); // within bounding box of Bern, but outside the zone
        Assert.assertNull(z);
    }
}