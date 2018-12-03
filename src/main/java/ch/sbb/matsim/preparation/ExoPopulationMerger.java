package ch.sbb.matsim.preparation;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;
import org.matsim.utils.objectattributes.ObjectAttributesXmlWriter;

public class ExoPopulationMerger {
    public static void main(String[] args)  {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        String pathOut = args[0];

        int i = 1;
        while(args[i] != null)  {
            new PopulationReader(scenario).readFile(args[i] + "plans.xml.gz");
            new ObjectAttributesXmlReader(scenario.getPopulation().getPersonAttributes()).readFile(args[i] + "personAttributes.xml.gz");
        }

        new PopulationWriter(scenario.getPopulation()).write(pathOut + "plans.xml.gz");
        new ObjectAttributesXmlWriter(scenario.getPopulation().getPersonAttributes()).writeFile(pathOut + "personAttributes.xml.gz");
    }
}
