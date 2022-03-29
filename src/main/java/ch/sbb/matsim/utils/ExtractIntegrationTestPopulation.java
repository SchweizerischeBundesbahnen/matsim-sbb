package ch.sbb.matsim.utils;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ExtractIntegrationTestPopulation {
    public static void main(String[] args) {
        String inputPop = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220114_MOBi_3.3\\2017\\sim\\3.3.2017.0\\prepared\\mobi_plans.xml.gz";
        String outputPop = "c:\\devsbb\\population.xml";
        int residence_zone_id = 120201001;
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(inputPop);
        List<Id<Person>> testAgents = scenario.getPopulation().getPersons().values()
                .stream().filter(person -> (int) person.getAttributes().getAttribute("residence_zone_id") == residence_zone_id)
                .map(person -> person.getId())
                .collect(Collectors.toList());
        Collections.shuffle(testAgents);
        Population pop2 = PopulationUtils.createPopulation(ConfigUtils.createConfig());
        for (int i = 0; i < 20; i++) {
            Id<Person> personId = testAgents.get(i);
            pop2.addPerson(scenario.getPopulation().getPersons().get(personId));
        }
        new PopulationWriter(pop2).write(outputPop);
    }
}
