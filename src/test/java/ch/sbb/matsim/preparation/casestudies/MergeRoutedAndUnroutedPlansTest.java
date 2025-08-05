package ch.sbb.matsim.preparation.casestudies;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Coord;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MergeRoutedAndUnroutedPlansTest {

    @Test
    public void prepareRelevantFacilities() {
        List<String> facilities = Collections.singletonList("test/input/scenarios/mobi31test/facilities.xml.gz");
        Set<String> relevantZones = Collections.singleton("120201001");
        Zones zones = ZonesLoader.loadZones("zones", "test/input/scenarios/mobi31test/zones/andermatt-zones.shp", Variables.ZONE_ID);
        var relevantFacilities = MergeRoutedAndUnroutedPlans.prepareRelevantFacilities(relevantZones, zones, facilities);
        Assertions.assertEquals(162, relevantFacilities.size());

        List<String> facilitiesEmpty = Collections.singletonList("-");
        relevantFacilities = MergeRoutedAndUnroutedPlans.prepareRelevantFacilities(relevantZones, zones, facilitiesEmpty);
        Assertions.assertEquals(0, relevantFacilities.size());

    }

    @Test
    public void isCoordinWhiteListZone() {

        Set<String> relevantZones = Collections.singleton("120201001");
        Zones zones = ZonesLoader.loadZones("zones", "test/input/scenarios/mobi31test/zones/andermatt-zones.shp", Variables.ZONE_ID);
        Coord in = new Coord(2690262, 1165213);
        Coord out = new Coord(0, 0);
        Assertions.assertTrue(MergeRoutedAndUnroutedPlans.isCoordinWhiteListZone(relevantZones, zones, in));
        Assertions.assertFalse(MergeRoutedAndUnroutedPlans.isCoordinWhiteListZone(relevantZones, zones, out));

    }
}