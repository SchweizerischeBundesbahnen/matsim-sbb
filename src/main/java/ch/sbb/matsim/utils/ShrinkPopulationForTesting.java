package ch.sbb.matsim.utils;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

public class ShrinkPopulationForTesting {

    public static void main(String[] args) {
        String inputPlans = "C:\\devsbb\\data\\CH.10pct.2016\\CH.10pct.2016.output_plans.xml.gz";
        String outputPlans = "C:\\devsbb\\data\\CH.10pct.2016\\CH.10pct.2016.output_plans_test.xml.gz";
        Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        StreamingPopulationReader streamingPopulationReader = new StreamingPopulationReader(scenario);
        StreamingPopulationWriter spw = new StreamingPopulationWriter(0.05);
        spw.startStreaming(outputPlans);
        streamingPopulationReader.addAlgorithm(spw);
        streamingPopulationReader.readFile(inputPlans);
        spw.closeStreaming();

    }
}
