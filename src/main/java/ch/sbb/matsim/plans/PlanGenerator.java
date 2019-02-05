package ch.sbb.matsim.plans;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.abm.AbmConverter;
import ch.sbb.matsim.plans.facilities.FacilitiesReader;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.facilities.ActivityFacilities;

import java.util.Set;


public class PlanGenerator {

    public static void main(final String[] args)  {

        final String pathToABM = args[0];
        final String pathToSynPop = args[1];
        final String pathToFacilties = args[2];
        final String pathToShapeFile = args[3];
        final String pathTopPlanOutputDir = args[4];
        final String pathToFacilityOutputDir = args[5];

        final FacilitiesReader facilitiesReader = new FacilitiesReader(";");
        Set<String> facilityAttributesToKeep = CollectionUtils.stringToSet(Variables.T_ZONE);
        ActivityFacilities facilities = facilitiesReader.convert(pathToFacilties, pathToShapeFile, pathToFacilityOutputDir,
                facilityAttributesToKeep);

        final AbmConverter abmConverter = new AbmConverter();
        abmConverter.read(pathToABM, ";");
        abmConverter.create_population();
        abmConverter.addSynpopAttributes(pathToSynPop);
        abmConverter.adjustModeIfNoLicense();
        abmConverter.addHomeFacilityAttributes(facilities, Variables.T_ZONE);
        abmConverter.addHomeFacilityAttributes(facilities, Variables.MS_REGION);
        abmConverter.writeOutputs(pathTopPlanOutputDir);
    }
}
