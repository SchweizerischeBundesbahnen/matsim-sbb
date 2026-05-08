package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.config.SBBIntermodalConfiggroup;
import ch.sbb.matsim.config.SBBIntermodalModeParameterSet;
import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.Config;

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
}



