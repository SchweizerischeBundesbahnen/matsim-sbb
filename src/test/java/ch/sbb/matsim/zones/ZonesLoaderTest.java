package ch.sbb.matsim.zones;

import ch.sbb.matsim.config.ZonesListConfigGroup;
import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Id;

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
        ZonesListConfigGroup cfg = new ZonesListConfigGroup();
        cfg.addZones(new ZonesListConfigGroup.ZonesParameterSet("testZones", "src/test/resources/shapefiles/AccessTime/accesstime_zone.SHP", "ID"));
        ZonesCollections zonesCollections = new ZonesCollections();
        ZonesLoader.loadAllZones(cfg, zonesCollections);

        Zones testZones = zonesCollections.getZones(Id.create("testZones", Zones.class));
        Assert.assertNotNull(testZones);
        Assert.assertEquals(5, testZones.size());
    }
}