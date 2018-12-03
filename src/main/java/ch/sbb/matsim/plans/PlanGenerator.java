package ch.sbb.matsim.plans;

import ch.sbb.matsim.plans.abm.AbmConverter;
import ch.sbb.matsim.plans.facilities.FacilitiesReader;
import org.matsim.api.core.v01.population.Population;


public class PlanGenerator {

    public static void main(final String[] args)  {

        final String pathToABM = args[0];
        final String pathToSynPop = args[1];
        final String pathToFacilties = args[2];
        final String pathToOutputDir = args[3];

        final AbmConverter abmConverter = new AbmConverter();
        abmConverter.read(pathToABM, ",");
        Population population = abmConverter.create_population();
        population = abmConverter.addSynpopAttributes(population, pathToSynPop);
        abmConverter.writeOutputs(pathToOutputDir, population);

        final FacilitiesReader facilitiesReader = new FacilitiesReader(",");
        facilitiesReader.convert(pathToFacilties, pathToOutputDir);
    }
}
