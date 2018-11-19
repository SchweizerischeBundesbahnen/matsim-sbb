package ch.sbb.matsim.plans;

import ch.sbb.matsim.plans.abm.AbmConverter;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;


public class PlanGenerator {

    public static void main(final String[] args)  {

        final String pathToABM = args[0];
        final String pathToSynPop = args[1];
        final String pathToMATSimNetwork = args[2];
        final String pathToOutputDir = args[3];

        final AbmConverter abmConverter = new AbmConverter();
        abmConverter.read(pathToABM, ",");
        Population population = abmConverter.create_population();
        population = abmConverter.addSynpopAttributes(population, pathToSynPop);
        //new MatsimNetworkReader(scenario.getNetwork()).readFile(pathToMATSimNetwork);
        abmConverter.writeOutputs(pathToOutputDir, population);
    }
}
