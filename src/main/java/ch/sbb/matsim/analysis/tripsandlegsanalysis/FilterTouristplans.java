package ch.sbb.matsim.analysis.tripsandlegsanalysis;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;

import java.util.List;

import static org.matsim.core.config.ConfigUtils.createConfig;
import static org.matsim.core.scenario.ScenarioUtils.createScenario;

public class FilterTouristplans {

    public static void main(String[] args) {
        String inputFile1 = "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_4.0\\2017\\sim\\3.3.2017.7.50pct\\output_slice0\\M332017.7.output_experienced_plans.xml.gz";
        String inputFile2 = "\\\\wsbbrz0283\\mobi\\50_Ergebnisse\\MOBi_4.0\\2017\\sim\\3.3.2017.7.50pct\\output_slice1\\M332017.7.output_experienced_plans.xml.gz";
        String plansFile = "C:\\devsbb\\outputTouristplans.xml.gz";
        Scenario scenario = createScenario(createConfig());
        new PopulationReader(scenario).readFile(plansFile);
        var files = List.of(inputFile2, inputFile1);
        StreamingPopulationWriter streamingPopulationWriter = new StreamingPopulationWriter();
        streamingPopulationWriter.startStreaming("C:\\devsbb\\outputTouristExpplans.xml.gz");
        for (String file : files) {
            StreamingPopulationReader spr = new StreamingPopulationReader(createScenario(createConfig()));
            spr.addAlgorithm(person -> {
                if (scenario.getPopulation().getPersons().containsKey(person.getId())) {
                    streamingPopulationWriter.run(person);
                }
            });
            spr.readFile(file);
        }
        streamingPopulationWriter.closeStreaming();

    }
}
