package ch.sbb.matsim.preparation;

import ch.sbb.matsim.preparation.cutter.BetterPopulationReader;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * @author jbischoff / SBB
 */
public class PopulationSlicer {
    Random random = MatsimRandom.getRandom();

    /*
    Randomly slices a population in n parts
     */
    public static void main(String[] args) {
        String inputPopulation = args[0];
        int slices = Integer.parseInt(args[1]);
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        BetterPopulationReader.readSelectedPlansOnly(scenario, new File(inputPopulation));
        new PopulationSlicer().run(scenario.getPopulation(), inputPopulation.replace(".xml.gz", ""), slices);

    }

    public void run(Population population, String outputName, int slices) {
        List<Id<Person>> personIds = new ArrayList<>(population.getPersons().keySet());
        Collections.shuffle(personIds, random);
        int partitionsize = personIds.size() / slices;

        for (int i = 0; i < slices; i++) {
            StreamingPopulationWriter streamingPopulationWriter = new StreamingPopulationWriter();
            streamingPopulationWriter.startStreaming(outputName + "_" + i + ".xml.gz");
            for (int j = 0; j < partitionsize; j++) {
                int personNo = i * partitionsize + j;
                streamingPopulationWriter.run(population.getPersons().get(personIds.get(personNo)));
            }
            streamingPopulationWriter.closeStreaming();
        }


    }

}
