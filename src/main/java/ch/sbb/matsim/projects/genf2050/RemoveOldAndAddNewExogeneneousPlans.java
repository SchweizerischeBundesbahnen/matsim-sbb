package ch.sbb.matsim.projects.genf2050;

import ch.sbb.matsim.preparation.slicer.PopulationSlicer;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

public class RemoveOldAndAddNewExogeneneousPlans {

    public static void main(String[] args) throws IOException {
        String oldExoPlans = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220114_MOBi_3.3\\2050\\plans_exogeneous\\cb_rail\\100pct\\plans.xml.gz";
        String pattern = "LEX";
        String newExoPlans = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220411_Genf_2050\\pt\\exogener_Verkehr\\test-sim\\Ge_exo.xml";
        String outputFile = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220411_Genf_2050\\plans_exogeneous\\cb_rail\\100pct\\plans.xml.gz";

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Scenario scenario2 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(oldExoPlans);
        new PopulationReader(scenario2).readFile(newExoPlans);

        List<Id<Person>> toRemove = scenario.getPopulation().getPersons()
                .keySet()
                .stream()
                .filter(personId -> personId.toString().contains(pattern))
                .collect(Collectors.toList());
        System.out.println("Removing " + toRemove.size() + " plans with pattern " + pattern);
        toRemove.forEach(personId -> scenario.getPopulation().removePerson(personId));

        scenario2.getPopulation().getPersons().values().forEach(person -> scenario.getPopulation().addPerson(person));

        new PopulationWriter(scenario2.getPopulation()).write(outputFile);
        PopulationSlicer.main(new String[]{outputFile, "-", "10"});
    }
}
