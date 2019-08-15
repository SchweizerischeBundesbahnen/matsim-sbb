package ch.sbb.matsim.preparation.cutter;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;

public class TestPopRead {

    public static void main(String[] args) {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile("\\\\k13536\\mobi\\40_Projekte\\20190805_ScenarioCutter\\20190812_thun_10pct\\input\\population.xml.gz");
        new ObjectAttributesXmlReader(scenario.getPopulation().getPersonAttributes()).readFile("\\\\k13536\\mobi\\40_Projekte\\20190805_ScenarioCutter\\20190812_thun_10pct\\input\\personAttributes.xml.gz");

        for (Id<Person> p : scenario.getPopulation().getPersons().keySet()) {
            String subpopulation = (String) scenario.getPopulation().getPersonAttributes().getAttribute(p.toString(), "subpopulation");
            if (subpopulation == null) {
                System.out.println(p);
            }
        }

    }

}
