package ch.sbb.matsim.preparation.cutter;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.File;

public class TestPopRead {

    public static void main(String[] args) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile("C:\\devsbb\\data\\CH.10pct.2016\\CH.10pct.2016.output_plans.xml.gz");
        scenario.getPopulation().getPersons().values().parallelStream().forEach(p -> PersonUtils.removeUnselectedPlans(p));

        scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        BetterPopulationReader.readSelectedPlansOnly(scenario, new File("C:\\devsbb\\data\\CH.10pct.2016\\CH.10pct.2016.output_plans.xml.gz"));

    }
}
