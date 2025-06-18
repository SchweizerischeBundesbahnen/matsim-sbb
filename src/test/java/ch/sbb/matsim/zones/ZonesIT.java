package ch.sbb.matsim.zones;

import org.junit.Assert;
import org.junit.jupiter.api.Test;

import java.io.File;

public class ZonesIT {

    @Test
    public void runZonesIT() throws Exception {
        Zones zones = ZonesLoader.loadZones("testZones", "test/input/scenarios/mobi50test/zones/mobi-zones.shp");
        zones.getZones().forEach(zone -> {
            Double accsib_car = (Double) zone.getAttributes().getAttribute("accsib_car");
            Double accsib_car_div = accsib_car / 1000.0;
            zone.getAttributes().putAttribute("accsib_car_div", accsib_car_div);
        });
        String pathname = "test/output/zones/mobi-zones.gpkg";
        File file = new File(pathname);
        file.getParentFile().mkdirs();
        file.delete();
        ZoneGeoPackageWriter.writeGeoPackage(zones, pathname);
        Zones reloadedZones = ZonesLoader.loadZones("testZones", pathname);
        reloadedZones.getZones().forEach(zone -> {
            Double accsib_car_div = (Double) zone.getAttributes().getAttribute("accsib_car_div");
            Double accsib_car_div1 = (Double) zones.getZone(zone.getId()).getAttribute("accsib_car_div");
            Assert.assertEquals(accsib_car_div1, accsib_car_div, 0.0001);
            if (accsib_car_div == null) {
                throw new RuntimeException("accsib_car_div is null for zone " + zone.getId());
            }
        });
        file.delete();

    }
}
