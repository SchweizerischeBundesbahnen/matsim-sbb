package ch.sbb.matsim.preparation.PopulationSampler;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.ArrayList;


public class SBBPopulationSampler {

    public static void main(String[] args)  {
        if (args.length != 3) {
            System.err.println("Wrong number of arguments");
            return;
        }
        String inputPopulation = args[0];
        double fraction = Double.valueOf(args[1]);
        String outputPopulation = args[2];

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        new PopulationReader(scenario).readFile(inputPopulation);

        SBBPopulationSampler sampler = new SBBPopulationSampler();
        sampler.sample(scenario.getPopulation(), fraction);

        new PopulationWriter(scenario.getPopulation()).write(outputPopulation);
    }

    public void sample(Population population, double fraction) {

        ArrayList<Id<Person>> toDelete = new ArrayList<>();

        MatsimRandom.reset(1);

        for (Person person : population.getPersons().values()) {
            if (MatsimRandom.getRandom().nextDouble() > fraction) {
                toDelete.add(person.getId());
            }
        }

        for (Id<Person> pId : toDelete) {
            population.removePerson(pId);
        }
    }
}

