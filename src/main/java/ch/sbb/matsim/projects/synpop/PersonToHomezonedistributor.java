package ch.sbb.matsim.projects.synpop;

import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesLoader;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.utils.geometry.transformations.TransformationFactory;

import java.nio.file.Paths;
import java.util.Random;
import java.util.concurrent.Executors;

public class PersonToHomezonedistributor {

    public static void main(String[] args) {

        ArbitraryBuildingOSMParser arbitraryBuildingOSMParser = new ArbitraryBuildingOSMParser(TransformationFactory.getCoordinateTransformation(TransformationFactory.WGS84, TransformationFactory.CH1903_LV03_Plus), Executors.newWorkStealingPool());
        arbitraryBuildingOSMParser.parse(Paths.get(args[0]));
        Zones zones = ZonesLoader.loadZones("ID_Zone", args[1], "ID_Zone");
        arbitraryBuildingOSMParser.assignZones(zones);
        Random random = MatsimRandom.getRandom();
        var selector = arbitraryBuildingOSMParser.prepareRandomDistributor(true,random);
        

        System.out.println("done");

    }
}
