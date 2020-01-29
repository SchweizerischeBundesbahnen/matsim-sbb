package ch.sbb.matsim.preparation.cutter;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.File;

/**
 * A helper class to load a population with only the selected plans for the persons.
 *
 * @author mrieser
 */
public class BetterPopulationReader {

    public static void readSelectedPlansOnly(Scenario scenario, File file) {
        Population population = scenario.getPopulation();
        Scenario scenario1 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        StreamingPopulationReader r = new StreamingPopulationReader(scenario1);
        r.addAlgorithm(person -> {
            Plan selectedPlan = person.getSelectedPlan();
            person.getPlans().removeIf(plan -> plan != selectedPlan);
            population.addPerson(person);
        });
        r.readFile(file.getAbsolutePath());
    }

}
