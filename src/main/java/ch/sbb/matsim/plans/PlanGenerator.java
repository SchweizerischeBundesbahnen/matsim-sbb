package ch.sbb.matsim.plans;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.abm.AbmConverter;
import ch.sbb.matsim.plans.facilities.FacilitiesReader;
import java.util.Set;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.utils.collections.CollectionUtils;
import org.matsim.facilities.ActivityFacilities;

public class PlanGenerator {

	public static void main(final String[] args) {

		final String tripsFileABM = args[0];
		final String personsFileABM = args[1];
		final String pathToSynPop = args[2];
		final String pathToFacilties = args[3];
		final String pathToShapeFile = args[4];
		final String pathTopPlanOutputDir = args[5];
		final String pathToFacilityOutputDir = args[6];

		final FacilitiesReader facilitiesReader = new FacilitiesReader(";");
		Set<String> facilityAttributesToKeep = CollectionUtils.stringToSet(Variables.T_ZONE);
		ActivityFacilities facilities = facilitiesReader.convert(pathToFacilties, pathToShapeFile, pathToFacilityOutputDir,
				facilityAttributesToKeep);

		final AbmConverter abmConverter = new AbmConverter();
		abmConverter.read(tripsFileABM, personsFileABM);
		Population population = abmConverter.create_population();
		abmConverter.addSynpopAttributes(population, pathToSynPop);
		abmConverter.adjustModeIfNoLicense(population);
		abmConverter.addHomeFacilityAttributes(population, facilities, Variables.T_ZONE);
		abmConverter.addHomeFacilityAttributes(population, facilities, Variables.MS_REGION);
		abmConverter.createInitialEndTimeAttribute(population);
		abmConverter.writeOutputs(population, pathTopPlanOutputDir);
	}
}
