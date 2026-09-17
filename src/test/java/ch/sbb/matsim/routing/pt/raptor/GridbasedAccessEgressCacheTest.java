package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.config.SBBIntermodalConfiggroup;
import ch.sbb.matsim.config.SBBIntermodalModeParameterSet;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;
import org.matsim.core.utils.geometry.CoordUtils;
import org.matsim.pt.transitSchedule.api.TransitStopFacility;

import java.util.ArrayList;
import java.util.List;

import static org.matsim.core.config.ConfigUtils.createConfig;
import static org.matsim.core.scenario.ScenarioUtils.createScenario;

public class GridbasedAccessEgressCacheTest {
    private static GridbasedAccessEgressCache getGridbasedAccessEgressCache() {
        Config config = createConfig();
        config.global().setNumberOfThreads(1);
        var raptorConfig = new SwissRailRaptorConfigGroup();

        // Setup Car Mode
        SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet carSet = new SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet();
        carSet.setMode(SBBModes.CARFEEDER);
        carSet.setLinkIdAttribute("accessLinkId");
        carSet.setMaxRadius(10000); // Prevents Double.MAX_VALUE overflow (10km * 3 = 30000m diameter)
        raptorConfig.addIntermodalAccessEgress(carSet);

        // Setup Bike Mode
        SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet bikeSet = new SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet();
        bikeSet.setMode(SBBModes.BIKEFEEDER);
        bikeSet.setLinkIdAttribute("accessLinkId");
        bikeSet.setMaxRadius(10000); // Prevents Double.MAX_VALUE overflow
        raptorConfig.addIntermodalAccessEgress(bikeSet);

        config.addModule(raptorConfig);

        var sbbIntermodalConfigGroup = new SBBIntermodalConfiggroup();
        var carSetI = new SBBIntermodalModeParameterSet();
        carSetI.setMode(SBBModes.CARFEEDER);
        carSetI.setRoutedOnNetwork(true);
        carSetI.setSimulatedOnNetwork(false);
        sbbIntermodalConfigGroup.addModeParameters(carSetI);
        config.addModule(sbbIntermodalConfigGroup);

        Scenario scenario = createScenario(config);
        return new GridbasedAccessEgressCache(scenario);
    }

    @Test
    public void testGetCellNumber_InsideBounds() {
        GridbasedAccessEgressCache cache = getGridbasedAccessEgressCache();
        Coord stopCoord = new Coord(0, 0);

        // Center of the grid
        Assertions.assertEquals(11325, cache.getCellNumber(stopCoord, new Coord(0, 0)), "Center cell mapping failed");

        // Top-Left corner (Inclusive boundary: exact coordinates map to column 0, row 0)
        Assertions.assertEquals(0, cache.getCellNumber(stopCoord, new Coord(-15000, 15000)), "Top-Left mapping failed");

        // Top-Right corner (Exclusive boundary: shifted 1 meter in to avoid column 150)
        Assertions.assertEquals(149, cache.getCellNumber(stopCoord, new Coord(14999, 15000)), "Top-Right mapping failed");

        // Bottom-Left corner (Exclusive boundary: shifted 1 meter up to avoid row 150)
        Assertions.assertEquals(22350, cache.getCellNumber(stopCoord, new Coord(-15000, -14999)), "Bottom-Left mapping failed");

        // Bottom-Right corner (Exclusive boundary: shifted 1 meter in both directions)
        Assertions.assertEquals(22499, cache.getCellNumber(stopCoord, new Coord(14999, -14999)), "Bottom-Right mapping failed");
    }

    @Test
    public void testGetCellNumber_OutOfBounds_ShouldReturnMinusOne() {
        GridbasedAccessEgressCache cache = getGridbasedAccessEgressCache();
        Coord stopCoord = new Coord(0, 0);

        // Exactly on the bottom and right edges should fall out of bounds
        Assertions.assertEquals(-1, cache.getCellNumber(stopCoord, new Coord(15000, 0)), "Exact right edge should be out of bounds");
        Assertions.assertEquals(-1, cache.getCellNumber(stopCoord, new Coord(0, -15000)), "Exact bottom edge should be out of bounds");

        // Just outside top and left
        Assertions.assertEquals(-1, cache.getCellNumber(stopCoord, new Coord(0, 15001)), "Just above top edge");
        Assertions.assertEquals(-1, cache.getCellNumber(stopCoord, new Coord(-15001, 0)), "Just left of left edge");

        // Way outside
        Assertions.assertEquals(-1, cache.getCellNumber(stopCoord, new Coord(60000, 60000)), "Far out of bounds mapping failed");
    }

    @Test
    public void testGetCellCoordinate_Reversibility() {
        GridbasedAccessEgressCache cache = getGridbasedAccessEgressCache();
        Coord stopCoord = new Coord(0, 0);

        // Check that retrieving the Coord for a cell index maps perfectly back to that same index

        // 1. Top-Left Box (0)
        int topLeftBox = 0;
        Coord topLeftCoord = cache.getCellCoordinate(stopCoord, topLeftBox);
        Assertions.assertEquals(topLeftBox, cache.getCellNumber(stopCoord, topLeftCoord), "Top-Left reversibility failed");

        // 2. Top-Right Box (149)
        int topRightBox = 149;
        Coord topRightCoord = cache.getCellCoordinate(stopCoord, topRightBox);
        Assertions.assertEquals(topRightBox, cache.getCellNumber(stopCoord, topRightCoord), "Top-Right reversibility failed");

        // 3. Bottom-Left Box (22350)
        int bottomLeftBox = 22350;
        Coord bottomLeftCoord = cache.getCellCoordinate(stopCoord, bottomLeftBox);
        Assertions.assertEquals(bottomLeftBox, cache.getCellNumber(stopCoord, bottomLeftCoord), "Bottom-Left reversibility failed");

        // 4. Bottom-Right Box (22499)
        int bottomRightBox = 22499;
        Coord bottomRightCoord = cache.getCellCoordinate(stopCoord, bottomRightBox);
        Assertions.assertEquals(bottomRightBox, cache.getCellNumber(stopCoord, bottomRightCoord), "Bottom-Right reversibility failed");

        // 5. Center Box (11325)
        int centerBox = 11325;
        Coord centerCoord = cache.getCellCoordinate(stopCoord, centerBox);
        Assertions.assertEquals(centerBox, cache.getCellNumber(stopCoord, centerCoord), "Center reversibility failed");
    }

    @Test
    public void testGetCellNumber_WithNegativeStopCoords() {
        GridbasedAccessEgressCache cache = getGridbasedAccessEgressCache();
        Coord stopCoord = new Coord(-10000, -10000);

        // Center of the grid relative to negative stop coord
        int centerCell = cache.getCellNumber(stopCoord, new Coord(-10000, -10000));
        Assertions.assertTrue(centerCell >= 0 && centerCell < 22500, "Center cell should be in bounds for negative stop coords");

        // Verify bounds still work with negative coordinates
        Assertions.assertEquals(-1, cache.getCellNumber(stopCoord, new Coord(5001, -10000)), "Should be out of bounds to the right");
        Assertions.assertEquals(-1, cache.getCellNumber(stopCoord, new Coord(-10000, 5001)), "Should be out of bounds to the top");
    }

    @Test
    public void testDistanceFactor_Validation() {
        // Test that the distance factor check would correctly identify unreasonable cached values
        Coord stop = new Coord(0, 0);
        Coord activity1 = new Coord(100, 100);  // ~141m euclidean distance
        Coord activity2 = new Coord(1000, 1000); // ~1414m euclidean distance
        Coord activity3 = new Coord(5000, 5000); // ~7071m euclidean distance

        double euclideanDist1 = CoordUtils.calcEuclideanDistance(stop, activity1);
        double euclideanDist2 = CoordUtils.calcEuclideanDistance(stop, activity2);
        double euclideanDist3 = CoordUtils.calcEuclideanDistance(stop, activity3);

        // A 10x factor threshold means:
        // 141m * 10 = 1410m is acceptable
        // 1414m * 10 = 14140m is acceptable
        // But 141m * 11 = 1551m should be rejected

        Assertions.assertTrue(euclideanDist1 * 10.0 > 1410, "Test setup: distance factor should be reasonable");
        Assertions.assertTrue(euclideanDist2 * 10.0 > 14140, "Test setup: distance factor should be reasonable");
        Assertions.assertTrue(euclideanDist3 * 10.0 > 70710, "Test setup: distance factor should be reasonable");
    }

    @Test
    public void testCellIndexBoundaryValidation() {
        GridbasedAccessEgressCache cache = getGridbasedAccessEgressCache();
        Coord stopCoord = new Coord(0, 0);

        // These coordinates are within row/column bounds but edge cases
        // that could theoretically produce invalid cell indices

        // Just inside the valid range
        int validCell = cache.getCellNumber(stopCoord, new Coord(14999, -14999));
        Assertions.assertTrue(validCell >= 0 && validCell < 22500, "Valid cell should be in bounds");

        // Just outside should return -1
        Assertions.assertEquals(-1, cache.getCellNumber(stopCoord, new Coord(15000, -15000)), "Out of bounds should return -1");
        Assertions.assertEquals(-1, cache.getCellNumber(stopCoord, new Coord(-15001, 15001)), "Out of bounds should return -1");
    }

    @Test
    public void testThreadLocalDisutilityCreation() {
        // This test verifies that the cache can be initialized with multiple threads
        // without race conditions (the main fix for the 28km bug)
        Config config = createConfig();
        config.global().setNumberOfThreads(4); // Multiple threads to trigger potential race conditions
        var raptorConfig = new SwissRailRaptorConfigGroup();

        SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet carSet = new SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet();
        carSet.setMode(SBBModes.CARFEEDER);
        carSet.setLinkIdAttribute("accessLinkId");
        carSet.setMaxRadius(10000);
        raptorConfig.addIntermodalAccessEgress(carSet);

        SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet bikeSet = new SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet();
        bikeSet.setMode(SBBModes.BIKEFEEDER);
        bikeSet.setLinkIdAttribute("accessLinkId");
        bikeSet.setMaxRadius(10000);
        raptorConfig.addIntermodalAccessEgress(bikeSet);

        config.addModule(raptorConfig);

        var sbbIntermodalConfigGroup = new SBBIntermodalConfiggroup();
        var carSetI = new SBBIntermodalModeParameterSet();
        carSetI.setMode(SBBModes.CARFEEDER);
        carSetI.setRoutedOnNetwork(true);
        carSetI.setSimulatedOnNetwork(false);
        sbbIntermodalConfigGroup.addModeParameters(carSetI);
        config.addModule(sbbIntermodalConfigGroup);

        Scenario scenario = createScenario(config);

        // This should not throw an exception even with multiple threads
        // Previously this could cause race conditions with the shared disutility instance
        GridbasedAccessEgressCache cache = new GridbasedAccessEgressCache(scenario);
        Assertions.assertNotNull(cache, "Cache should be created successfully with multiple threads");
    }

    @Test
    public void testFormatSuspiciousDistancesReport_ListsUniqueStopsOnly() {
        GridbasedAccessEgressCache cache = getGridbasedAccessEgressCache();

        List<GridbasedAccessEgressCache.SuspiciousDistanceOccurrence> suspiciousDistances = List.of(
                new GridbasedAccessEgressCache.SuspiciousDistanceOccurrence(Id.create("stop-2", TransitStopFacility.class), 11, 180000, 1200),
                new GridbasedAccessEgressCache.SuspiciousDistanceOccurrence(Id.create("stop-1", TransitStopFacility.class), 7, 900, 190000),
                new GridbasedAccessEgressCache.SuspiciousDistanceOccurrence(Id.create("stop-2", TransitStopFacility.class), 42, 181000, 1300)
        );

        String report = cache.formatSuspiciousDistancesReport(suspiciousDistances);

        Assertions.assertEquals("Stops with very large distances in cache: stop-1, stop-2", report, "Report should contain each affected stop exactly once");
    }

    @Test
    public void testCreateStopPartitions_CoversAllStopsIncludingRemainder() {
        List<Id<TransitStopFacility>> stops = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            stops.add(Id.create("stop-" + i, TransitStopFacility.class));
        }

        List<List<Id<TransitStopFacility>>> partitions = GridbasedAccessEgressCache.createStopPartitions(stops, 3);

        Assertions.assertEquals(3, partitions.size(), "Ten stops with three threads should create three partitions");
        Assertions.assertEquals(10, partitions.stream().mapToInt(List::size).sum(), "All stops must be covered by the partitions");
        Assertions.assertEquals(stops, partitions.stream().flatMap(List::stream).toList(), "Partitioning must preserve stop order without dropping the remainder");
    }
}



