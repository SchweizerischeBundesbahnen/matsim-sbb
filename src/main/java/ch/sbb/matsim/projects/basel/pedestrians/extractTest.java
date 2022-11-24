package ch.sbb.matsim.projects.basel.pedestrians;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.PopulationWriter;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;

public class extractTest {

    public static void main(String[] args) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Scenario scenario2 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile("\\\\wsbbrz0283\\mobi\\40_Projekte\\20220412_Basel_2050\\pedestrians_basel_sbb\\outputplans-basel-sbb.xml.gz");
        scenario2.getPopulation().addPerson(scenario.getPopulation().getPersons().get(Id.createPersonId("14089880")));
        new PopulationWriter(scenario2.getPopulation()).write("\\\\wsbbrz0283\\mobi\\40_Projekte\\20220412_Basel_2050\\pedestrians_basel_sbb\\testperson.xml");
    }
}
