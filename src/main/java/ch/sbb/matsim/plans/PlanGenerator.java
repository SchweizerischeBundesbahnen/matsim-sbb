package ch.sbb.matsim.plans;

import ch.sbb.matsim.plans.abm.AbmConverter;
import ch.sbb.matsim.plans.facilities.FacilitiesReader;
import org.matsim.facilities.ActivityFacilities;


public class PlanGenerator {

    public static void main(final String[] args)  {

        final String pathToABM = args[0];
        final String pathToSynPop = args[1];
        final String pathToFacilties = args[2];
        final String pathToShapeFile = args[3];
        final String pathToOutputDir = args[4];

        final FacilitiesReader facilitiesReader = new FacilitiesReader(",");
        ActivityFacilities facilities = facilitiesReader.convert(pathToFacilties, pathToShapeFile, pathToOutputDir);

        final AbmConverter abmConverter = new AbmConverter();
        abmConverter.read(pathToABM, ",");
        abmConverter.create_population();
        abmConverter.addSynpopAttributes(pathToSynPop);
        abmConverter.addHomeFacilityAttributes(facilities, "ms_region");
        abmConverter.writeOutputs(pathToOutputDir);
    }
}
