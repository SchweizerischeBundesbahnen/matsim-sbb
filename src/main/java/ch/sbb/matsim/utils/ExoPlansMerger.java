package ch.sbb.matsim.utils;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class ExoPlansMerger {

    public static void main(String[] args) {
        Map<String, String> exoplans = new HashMap<>();
        exoplans.put("\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\alle_xml\\airport-demand.xml.gz", "");
        exoplans.put("\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\alle_xml\\cb_rail-20240311_pop_rail_IPV_2015_Fr_100pct.xml", "cb_rail");
        exoplans.put("\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\alle_xml\\cb_rail-20240311_pop_rail_IPV_2015_Ge_100pct.xml", "cb_rail");
        exoplans.put("\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\alle_xml\\cb_rail-20240311_pop_rail_IPV_2015_Pe_100pct.xml", "cb_rail");
        exoplans.put("\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\alle_xml\\cb_road.xml.gz", "");
        exoplans.put("\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\alle_xml\\freight.xml.gz", "");
        exoplans.put("\\\\wsbbrz0283\\mobi\\40_Projekte\\20240207_MOBi_5.0\\plans_exogeneous\\alle_xml\\tourism_rail-plans.xml.gz", "tourism_rail");
        Random r = MatsimRandom.getRandom();
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        for (var p : exoplans.entrySet()) {
            Scenario scenario1 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
            new PopulationReader(scenario1).readFile(p.getKey());
            if (!p.getValue().isEmpty()) {
                scenario1.getPopulation().getPersons().values().forEach(person -> PopulationUtils.putSubpopulation(person, p.getValue()));
            }
            scenario1.getPopulation().getPersons().values().forEach(person -> {
                int slice = r.nextInt(10);
                person.getAttributes().putAttribute("slice", slice);
                if (slice == 0) {
                    scenario.getPopulation().addPerson(person);
                }
            });
        }
        new PopulationReader(scenario).readFile("C:\\devsbb\\plans_5_mobtools.plans.xml.gz");

        new PopulationWriter(scenario.getPopulation()).write("C:\\devsbb\\plans_5_mobtools.plans_merged.xml.gz");

    }
}
