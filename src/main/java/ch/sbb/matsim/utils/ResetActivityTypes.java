package ch.sbb.matsim.utils;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.core.scenario.ScenarioUtils;

public class ResetActivityTypes {

    public static void main(String[] args) {
        String inputPlans = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220404_Routenwahl_MIV\\sim\\mini_02\\prepared\\plans.xml.gz";

        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(scenario).readFile(inputPlans);

        for (Person p : scenario.getPopulation().getPersons().values()) {
            for (Plan plan : p.getPlans()) {
                TripStructureUtils.getActivities(plan, TripStructureUtils.StageActivityHandling.ExcludeStageActivities).forEach(activity ->
                        {
                            String baseActivityType = activity.getType().split("_")[0];
                            activity.setType(baseActivityType);

                        }
                );
            }
        }
        new PopulationWriter(scenario.getPopulation()).write("c:/devsbb/plans-resetat.xml.gz");
    }
}
