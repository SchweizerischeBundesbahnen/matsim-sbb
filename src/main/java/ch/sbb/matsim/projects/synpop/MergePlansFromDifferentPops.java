package ch.sbb.matsim.projects.synpop;

import ch.sbb.matsim.config.variables.Variables;
import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.List;

public class MergePlansFromDifferentPops {

    public static void main(String[] args) {
        String pop1 = "C:\\Users\\u229187\\Downloads\\19_va_car_reduced.output_plans.xml.gz";
        String pop2 = "C:\\devsbb\\19_va_car_reduced.plans.xml.gz";
        String outfile = "C:\\devsbb\\19_va_car_airport_fixed_plans.xml.gz";
        List<String> subpopsFromPop2 = List.of(Variables.AIRPORT_RAIL, Variables.AIRPORT_ROAD);
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        StreamingPopulationReader spr = new StreamingPopulationReader(scenario);
        StreamingPopulationWriter spw = new StreamingPopulationWriter();
        spw.startStreaming(outfile);
        spr.addAlgorithm(person -> {
            if (!subpopsFromPop2.contains(PopulationUtils.getSubpopulation(person))) {
                PersonUtils.removeUnselectedPlans(person);
                spw.run(person);
            }
        });
        spr.readFile(pop1);
        StreamingPopulationReader streamingPopulationReader = new StreamingPopulationReader(scenario);
        streamingPopulationReader.addAlgorithm(person -> {
            if (subpopsFromPop2.contains(PopulationUtils.getSubpopulation(person))) {
                PersonUtils.removeUnselectedPlans(person);
                spw.run(person);
            }
        });
        streamingPopulationReader.readFile(pop2);
        spw.closeStreaming();
    }
}
