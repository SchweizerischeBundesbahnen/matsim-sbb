package ch.sbb.matsim.preparation.PopulationSampler;

import java.util.ArrayList;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;


public class SBBPopulationSampler {

    public static void main(String[] args)  {
        String inputPopulation = args[0];
        String inputAttributes = args[1];
        double fraction = Double.valueOf(args[2]);
        String outputPopulation = args[3];
        String outputAttributes = args[4];

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        new PopulationReader(scenario).readFile(inputPopulation);
        new ObjectAttributesXmlReader(scenario.getPopulation().getPersonAttributes()).readFile(inputAttributes);

        SBBPopulationSampler sampler = new SBBPopulationSampler();
        sampler.sample(scenario.getPopulation(), fraction);

        new PopulationWriter(scenario.getPopulation()).write(outputPopulation);
        new ObjectAttributesXmlWriter(scenario.getPopulation().getPersonAttributes()).writeFile(outputAttributes);
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
            population.getPersonAttributes().removeAllAttributes(pId.toString());
        }
    }
}

