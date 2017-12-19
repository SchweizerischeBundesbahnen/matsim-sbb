package ch.sbb.matsim.analysis;

import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Coord;

public class LocateActTest {
    private String shapefile = "src/test/resources/shapefiles/CNB/CNB.SHP";
    private Coord cdf = new Coord(553419 , 216894 );
    private Coord cdf_inv = new Coord(216894 , 553419 );
    private Coord sion = new Coord(593997 , 120194);

    @Test
    public final void testChauxDeFond() {
        LocateAct actLocator = new LocateAct(shapefile);
        Assert.assertEquals("", actLocator.getZone(cdf).getAttribute("GMDNAME"));
    }

}
