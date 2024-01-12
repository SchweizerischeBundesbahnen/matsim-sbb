package ch.sbb.matsim.analysis.skims;

import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Coord;
import org.matsim.testcases.MatsimTestUtils;

class DiameterBasedCoordCondenserTest {

    @Test
    public void testAllCloseTogether() {
        DiameterBasedCoordCondenser condenser = new DiameterBasedCoordCondenser();
        Coord coord0 = new Coord(0, 0);
        Coord coord1 = new Coord(100, 100);
        Coord coord2 = new Coord(0, 100);
        Coord coord3 = new Coord(100, 0);
        Coord[] coords = new Coord[]{coord0, coord1, coord2, coord3};
        var result = condenser.aggregateCoords(coords);
        Assert.assertEquals(result.size(), 1);
        Assert.assertEquals(result.get(0).coord(), new Coord(50, 50));
        Assert.assertEquals(result.get(0).weight(), 4.0, MatsimTestUtils.EPSILON);
    }

    @Test
    public void testTwoAndTwoTogether() {
        DiameterBasedCoordCondenser condenser = new DiameterBasedCoordCondenser();
        Coord coord0 = new Coord(0, 0);
        Coord coord1 = new Coord(100, 100);
        Coord coord2 = new Coord(1000, 1000);
        Coord coord3 = new Coord(1000, 900);
        Coord[] coords = new Coord[]{coord0, coord1, coord2, coord3};
        var result = condenser.aggregateCoords(coords);
        Assert.assertEquals(result.size(), 2);
        Assert.assertEquals(result.get(0).weight(), 2.0, MatsimTestUtils.EPSILON);
        Assert.assertEquals(result.get(1).weight(), 2.0, MatsimTestUtils.EPSILON);
    }

}