package ch.sbb.matsim.rerouting;

import ch.sbb.matsim.analysis.skims.CalculateSkimMatrices;
import ch.sbb.matsim.config.variables.SBBModes;
import ch.sbb.matsim.config.variables.SBBModes.PTSubModes;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.Set;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.core.utils.misc.Time;

public class SkimMatrices {

    final static String YEAR = "2020";
    final static String TRANSIT = "rail";
    final static String TRY = "calc";
    final static String columNames = "Z:/99_Playgrounds/MD/Umlegung/Input/ZoneToNode.csv";
    final static String demand = "Z:/99_Playgrounds/MD/Umlegung/Input/Demand2018.omx";
    final static String saveFileInpout = "Z:/99_Playgrounds/MD/Umlegung/Input/" + YEAR + "/" + TRANSIT + "/saveFile.csv";
    final static String schedualFile = "Z:/99_Playgrounds/MD/Umlegung/Input/" + YEAR + "/" + TRANSIT + "/transitSchedule.xml.gz";
    final static String netwoekFile = "Z:/99_Playgrounds/MD/Umlegung/Input/" + YEAR + "/" + TRANSIT + "/transitNetwork.xml.gz";
    final static String output = "Z:/99_Playgrounds/MD/Umlegung/Results/" + YEAR + "/matrix";
    final static String zonesShapeFilename = "Z:/99_Playgrounds/MD/Umlegung/Input/zone/mobi-zones_ch_4326.shp";
    final static String facilitiesFilename = "Z:/99_Playgrounds/MD/Umlegung/Input/facilities/facilities.xml";
    final static double[] timesPt = {0. , 3600*12};


    public static void main(String[] args) throws IOException {
        Random r = new Random(4711L);
        CalculateSkimMatrices skims = new CalculateSkimMatrices(output, 1);
        skims.calculateSamplingPointsPerZoneFromFacilities(facilitiesFilename, 2, zonesShapeFilename, "zone_id", r, (f) -> 1.0);
        skims.writeSamplingPointsToFile(new File(output, "zone_coordinates.csv"));
        skims.calculateAndWritePTMatrices(netwoekFile, schedualFile, timesPt[0], timesPt[1], ConfigUtils.createConfig(), "", (line, route) -> route.getTransportMode().equals(PTSubModes.RAIL));

        skims.calculateAndWriteBeelineMatrix();
    }

}
