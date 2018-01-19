package ch.sbb.matsim.analysis;

import java.util.HashMap;
import java.util.Set;

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
import org.matsim.core.population.PersonUtils;
import org.matsim.core.scenario.ScenarioUtils;

import ch.sbb.matsim.config.PostProcessingConfigGroup;

public class PopulationToCSVTest {

    @Test
    public final void testPopulationPostProc() {

        PostProcessingConfigGroup pg = new PostProcessingConfigGroup();
        pg.setPersonAttributes("carAvail,hasLicense,gender,subpopulation");

        Config config = ConfigUtils.createConfig(pg);
        Scenario scenario = ScenarioUtils.createScenario(config);
        Population population = scenario.getPopulation();
        PopulationFactory populationFactory = population.getFactory();

        Person person = populationFactory.createPerson(Id.createPersonId("1"));

        PersonUtils.setCarAvail(person, "never");
        PersonUtils.setLicence(person, "driving");

        //PersonUtils.setAge(person, 1);
        //PersonUtils.setEmployed(person, true);
        //PersonUtils.setSex(person, "m");

        person.getAttributes().putAttribute("subpopulation", "regular");
        person.getAttributes().putAttribute("gender", "m");


        population.addPerson(person);

        PopulationToCSV tool = new PopulationToCSV(scenario);

        HashMap<String, String> a = tool.agents_writer.getData().get(0);

        Assert.assertEquals("never", a.get("carAvail"));
        Assert.assertEquals("m", a.get("gender"));
        Assert.assertEquals("driving", a.get("hasLicense"));

        Assert.assertEquals(a.get("subpopulation"),"regular");

        System.out.println(a);

    }

}
