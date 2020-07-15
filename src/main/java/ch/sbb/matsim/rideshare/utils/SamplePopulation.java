package ch.sbb.matsim.rideshare.utils;

import org.matsim.api.core.v01.Scenario;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.StreamingPopulationReader;
import org.matsim.core.population.io.StreamingPopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

public class SamplePopulation {

	public static void main(String[] args) {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		StreamingPopulationWriter streamingPopulationWriter = new StreamingPopulationWriter(0.01);
		streamingPopulationWriter.startStreaming("\\\\k13536\\mobi\\40_Projekte\\20190913_Ridesharing\\scenarios\\0.01_neuenburg\\input\\population.xml.gz");

		StreamingPopulationReader streamingPopulationReader = new StreamingPopulationReader(scenario);
		streamingPopulationReader.addAlgorithm(streamingPopulationWriter);
		streamingPopulationReader.readFile("\\\\k13536\\mobi\\40_Projekte\\20190913_Ridesharing\\scenarios\\0.01_neuenburg\\input\\1.0.population.xml.gz");
		streamingPopulationWriter.closeStreaming();
	}

}
