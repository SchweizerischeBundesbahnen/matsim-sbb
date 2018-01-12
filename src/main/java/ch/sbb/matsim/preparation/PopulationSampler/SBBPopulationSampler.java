package ch.sbb.matsim.preparation.PopulationSampler;

import java.util.ArrayList;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.gbl.MatsimRandom;


public class SBBPopulationSampler {

    public void sample(Population population, double fraction) {

        ArrayList<Id<Person>> toDelete = new ArrayList<>();

        MatsimRandom.reset(1);

        for (Person person : population.getPersons().values()) {
            if (MatsimRandom.getRandom().nextDouble() > fraction) {
                toDelete.add(person.getId());
            }
        }

        for (Id pId : toDelete) {
            population.removePerson(pId);
        }
    }
}

