package ch.sbb.matsim.routing.pt.raptor;

import ch.sbb.matsim.config.SwissRailRaptorConfigGroup;
import ch.sbb.matsim.config.variables.SBBModes;
import org.junit.Assert;
import org.junit.Test;
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
        SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet carSet = new SwissRailRaptorConfigGroup.IntermodalAccessEgressParameterSet();
        carSet.setMode(SBBModes.CARFEEDER);
        carSet.setLinkIdAttribute("accessLinkId");
        raptorConfig.addIntermodalAccessEgress(carSet);
        config.addModule(raptorConfig);
        Scenario scenario = createScenario(config);
        GridbasedAccessEgressCache cache = new GridbasedAccessEgressCache(scenario);
        return cache;
    }

    @Test
    public void testCalculateGridForStop() {

    }

    @Test
    public void testGetCellNumberAndCoordinates() {
        GridbasedAccessEgressCache cache = getGridbasedAccessEgressCache();
        Coord stopCoord = new Coord(0, 0);

        int center = cache.getCellNumber(stopCoord, new Coord(0, 0));
        Assert.assertEquals(11325, center);
        System.out.println(center);

        int edgeBottomRight = cache.getCellNumber(stopCoord, new Coord(15000, -15000));
        Assert.assertEquals(22650, edgeBottomRight);
        System.out.println(edgeBottomRight);


        int edgeTopLeft = cache.getCellNumber(stopCoord, new Coord(-15000, 15000));
        Assert.assertEquals(0, edgeTopLeft);
        System.out.println(edgeTopLeft);

        int edgeBottomLeft = cache.getCellNumber(stopCoord, new Coord(-15000, -14800));
        Assert.assertEquals(22350, edgeBottomLeft);
        System.out.println(edgeBottomLeft);

        int edgeTopRight = cache.getCellNumber(stopCoord, new Coord(15000, 15000));
        Assert.assertEquals(150, edgeTopRight);
        System.out.println(edgeTopRight);

        int out = cache.getCellNumber(stopCoord, new Coord(60000, 60000));
        System.out.println(out);

        int topLeftBox = 0;
        var topLeftCoord = cache.getCellCoordinate(stopCoord, topLeftBox);
        int topLeftReturn = cache.getCellNumber(stopCoord, topLeftCoord);
        Assert.assertEquals(topLeftBox, topLeftReturn);

        int topRightBox = 149;
        var topRightCoord = cache.getCellCoordinate(stopCoord, topRightBox);
        int topRightReturn = cache.getCellNumber(stopCoord, topRightCoord);
        Assert.assertEquals(topRightBox, topRightReturn);

        int bottomLeftBox = 22350;
        var bottomLeftCoord = cache.getCellCoordinate(stopCoord, bottomLeftBox);
        int bottomLeftReturn = cache.getCellNumber(stopCoord, bottomLeftCoord);
        Assert.assertEquals(bottomLeftBox, bottomLeftReturn);

        int bottomRightBox = 22499;
        var bottomRightCoord = cache.getCellCoordinate(stopCoord, bottomRightBox);
        int bottomRightReturn = cache.getCellNumber(stopCoord, bottomRightCoord);
        Assert.assertEquals(bottomRightBox, bottomRightReturn);

    }


}