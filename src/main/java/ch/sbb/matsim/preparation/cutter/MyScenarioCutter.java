package ch.sbb.matsim.preparation.cutter;

import ch.sbb.matsim.zones.ZonesLoader;

import java.io.IOException;

public class MyScenarioCutter {
    public static void main(String[] args) throws IOException {
        CutExtent inside = new ShapeExtent(ZonesLoader.loadZones
                ("id", "\\\\k13536\\mobi\\40_Projekte\\20190805_ScenarioCutter\\20190805_zones\\thun\\thun-agglo.shp", "ID"));
        CutExtent outside = new ShapeExtent(ZonesLoader.loadZones
                ("id", "\\\\k13536\\mobi\\40_Projekte\\20190805_ScenarioCutter\\20190805_zones\\thun\\thun-umgebung.shp", "ID"));

        ScenarioCutter.run("C:\\devsbb\\data\\CH.10pct.2016", "CH.10pct.2016", "C:\\devsbb\\data\\CH2016_thun_cut", 1.0, true, inside, outside);

    }

}
