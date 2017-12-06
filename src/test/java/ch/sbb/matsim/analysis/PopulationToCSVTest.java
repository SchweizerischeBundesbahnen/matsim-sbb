package ch.sbb.matsim.analysis;

import ch.sbb.matsim.config.PostProcessingConfigGroup;
import org.junit.Assert;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.Set;

public class PopulationToCSVTest {

    @Test
    public final void testPopulationPostProc() {

        PostProcessingConfigGroup pg = new PostProcessingConfigGroup();

        pg.setPersonCustumAttributes("carAvail,gender");
        pg.setPersonAttributes("subpopulation");

        Config config = ConfigUtils.createConfig(pg);
        Scenario scenario = ScenarioUtils.createScenario(config);
        Population population = scenario.getPopulation();
        PopulationFactory populationFactory = population.getFactory();
        Plan plan = populationFactory.createPlan();
        Person person = populationFactory.createPerson(Id.createPersonId("1"));
        person.getCustomAttributes().put("carAvail", "never");
        population.addPerson(person);

        PopulationToCSV tool = new PopulationToCSV(scenario);

        Set<String> t = new java.util.HashSet<>();
        t.add("carAvail");
        t.add("gender");
        t.add("subpopulation");

        // automatically added
        t.add("person_id");

        Set d = tool.agents_writer.getData().get(0).keySet();
        Assert.assertEquals(t, d);
        System.out.println();

    }

}
