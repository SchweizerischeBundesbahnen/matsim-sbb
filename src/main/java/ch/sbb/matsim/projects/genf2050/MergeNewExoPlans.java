package ch.sbb.matsim.projects.genf2050;

import ch.sbb.matsim.config.variables.Variables;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.List;

public class MergeNewExoPlans {

    public static void main(String[] args) {
        String plans0 = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220411_Genf_2050\\sim\\1.2-diam-50pct\\output_slice0\\diam.output_plans.xml.gz";
        String plans1 = "\\\\wsbbrz0283\\mobi\\40_Projekte\\20220411_Genf_2050\\sim\\1.2-diam-50pct\\output_slice1\\diam.output_plans.xml.gz";
        List<String> inputPlans = List.of(plans1, plans0);
        List<String> subPopsToRemove = List.of(Variables.CB_RAIL, Variables.CB_ROAD);
        String cb_rail = "";
        String cb_road = "";
        String outfile = "C:\\devsbb\\diam.output_plans50pct.xml";

        StreamingPopulationWriter streamingPopulationWriter = new StreamingPopulationWriter();
        streamingPopulationWriter.startStreaming(outfile);
        for (String s : inputPlans) {
            StreamingPopulationReader streamingPopulationReader = new StreamingPopulationReader(ScenarioUtils.createScenario(ConfigUtils.createConfig()));
            streamingPopulationReader.addAlgorithm(p ->
            {
                if (!subPopsToRemove.contains(PopulationUtils.getSubpopulation(p))) {
                    PersonUtils.removeUnselectedPlans(p);
                    streamingPopulationWriter.run(p);

                }
            });
            streamingPopulationReader.readFile(s);
        }

        Scenario cbRailScen = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Scenario cbRoadScen = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        new PopulationReader(cbRailScen).readFile(cb_rail);
        new PopulationReader(cbRoadScen).readFile(cb_road);
        List<Scenario> scens = List.of(cbRailScen, cbRoadScen);
        cbRailScen.getPopulation().getPersons().values().forEach(p -> p.getAttributes().putAttribute(Variables.SUBPOPULATION, Variables.CB_RAIL));
        cbRoadScen.getPopulation().getPersons().values().forEach(p -> p.getAttributes().putAttribute(Variables.SUBPOPULATION, Variables.CB_ROAD));
        for (var s : scens) {
            int i = 0;
            for (Person p : s.getPopulation().getPersons().values()) {
                if (i % 2 == 0) {
                    streamingPopulationWriter.run(p);
                }
                i++;

            }
        }

        streamingPopulationWriter.closeStreaming();
    }

}
