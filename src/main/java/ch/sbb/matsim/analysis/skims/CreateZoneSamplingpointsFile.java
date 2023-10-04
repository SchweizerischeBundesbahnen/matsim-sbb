package ch.sbb.matsim.analysis.skims;

import ch.sbb.matsim.zones.ZonesImpl;
import ch.sbb.matsim.zones.ZonesLoader;
import org.matsim.api.core.v01.Coord;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.utils.geometry.geotools.MGC;

import java.io.File;
import java.io.IOException;
import java.util.Random;

public class CreateZoneSamplingpointsFile {
    public static void main(String[] args) throws IOException {


        String outputDirectory = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20230825_Grenzguertel\\skims";
        int numberOfThreads = 2;
        CalculateSkimMatrices skims = new CalculateSkimMatrices(outputDirectory, numberOfThreads);

        String facilitiesFilename = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20230825_Grenzguertel\\skims\\facilities_merged.xml.gz";
        int numberOfPointsPerZone = 4;
        String zonesShapeFilename = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20230825_Grenzguertel\\10_Zones\\npvm_allezonen\\NUTS3_FR_korrigiert\\Verkehrszonen_Ausland_NPVM_2017_epsg2056.shp";
        String zonesIdAttributeName = "ID_Zone";
        Random r = MatsimRandom.getRandom();
        skims.calculateSamplingPointsPerZoneFromFacilities(facilitiesFilename, numberOfPointsPerZone, zonesShapeFilename, zonesIdAttributeName, r, f -> 1);
        var zonesCoord = skims.getCoordsPerZone();

        ZonesImpl zones = (ZonesImpl) ZonesLoader.loadZones(zonesIdAttributeName, zonesShapeFilename, zonesIdAttributeName);
        zones.getZones().stream().filter(zone -> !zonesCoord.containsKey(zone.getId().toString())).forEach(zone -> {
            Coord centre = MGC.coordinate2Coord(zone.getEnvelope().centre());
            Coord[] coords = new Coord[]{centre, centre, centre, centre};
            String zoneIdString = zone.getId().toString();
            zonesCoord.put(zoneIdString, coords);
            System.out.println("Added Samping points for Zone " + zoneIdString);
        });
        System.out.println("Zone Coord zones: " + zonesCoord.size());
        skims.writeSamplingPointsToFile(new File(outputDirectory, "zone_coordinates.csv"));


    }


}
