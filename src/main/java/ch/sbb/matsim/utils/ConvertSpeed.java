package ch.sbb.matsim.utils;

import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.models.utils.AttributeUtils;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

public class ConvertSpeed {

    static void main() {
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile("/Volumes/mobi/40_Projekte/20260513_MOBi_5.2/2023/plans/78_skims/output/78_skims.plans.xml.zst");
        scenario.getPopulation().getPersons().values().forEach(person -> {
            double pedSpeed = AttributeUtils.getAsDouble(person, SynpopConstants.PEDESTRIAN_SPEED, -1) / 3.6;
            if (pedSpeed > 0) {person.getAttributes().putAttribute(SynpopConstants.PEDESTRIAN_SPEED, pedSpeed);}

        });

        new PopulationWriter(scenario.getPopulation()).write("/Users/joschka.bischoff/Desktop/78_skims.plans.xml.zst");
    }
}
