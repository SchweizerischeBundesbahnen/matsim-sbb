package ch.sbb.matsim.analysis.PopulationSampler;


import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

import ch.sbb.matsim.config.PostProcessingConfigGroup;

public class TestFixture {

    Scenario scenario;
    Config config;

    TestFixture(int size, boolean addAttributes) {

        this.config = ConfigUtils.createConfig(new PostProcessingConfigGroup());

        this.scenario = ScenarioUtils.createScenario(config);

        Population population = this.scenario.getPopulation();

        PopulationFactory pf = population.getFactory();
        int i = 0;
        while (population.getPersons().size() < size) {
            i += 1;
            Person person = pf.createPerson(Id.create(i, Person.class));
            population.addPerson(person);
            if(addAttributes)
                person.getAttributes().putAttribute("attribute", "value");
        }
    }
}
