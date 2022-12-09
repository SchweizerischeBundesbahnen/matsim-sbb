/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis;

import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.csv.CSVWriter;
import java.io.IOException;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.households.HouseholdsReaderV10;
import org.matsim.utils.objectattributes.ObjectAttributesXmlReader;

public class PopulationToCSV {

	private final static Logger log = LogManager.getLogger(PopulationToCSV.class);

	private final Scenario scenario;

	public PopulationToCSV(Scenario scenario) {
		this.scenario = scenario;
	}

	public static void main(final String[] args) {
		Config config = ConfigUtils.loadConfig(args[0], new PostProcessingConfigGroup());
		String populationFile = args[1];

		Scenario scenario = ScenarioUtils.createScenario(config);
		config.plans().setInputFile(populationFile);

		new PopulationReader(scenario).readFile(config.plans().getInputFile());

		if (config.households().getInputFile() != null) {
			new HouseholdsReaderV10(scenario.getHouseholds()).readFile(config.households().getInputFile());
		}
		if (config.households().getInputHouseholdAttributesFile() != null) {
			new ObjectAttributesXmlReader(scenario.getHouseholds().getHouseholdAttributes()).readFile(config.households().getInputHouseholdAttributesFile());
		}

		new PopulationToCSV(scenario).write("agents.csv.gz", "planelements.csv.gz");
	}

	public void write(String filename) {
		this.write(filename + "agents.csv.gz", filename + "plan_elements.csv.gz");
	}

	public void write(String agentsFilename, String planElementsFilename) {
		PostProcessingConfigGroup ppConfig = ConfigUtils.addOrGetModule(this.scenario.getConfig(), PostProcessingConfigGroup.class);
		Population population = this.scenario.getPopulation();
		String[] attributes = ppConfig.getPersonAttributes().split(",");

		if (ppConfig.getWriteAgentsCSV()) {
			try (CSVWriter agentsWriter = new CSVWriter("", getColumns(attributes), agentsFilename)) {
				for (Person person : population.getPersons().values()) {
					agentsWriter.set("person_id", person.getId().toString());
					for (String attribute_name : attributes) {
						Object attribute;
						attribute = person.getAttributes().getAttribute(attribute_name);

						if (attribute != null) {
							agentsWriter.set(attribute_name, attribute.toString());
						}
					}
					agentsWriter.writeRow();
				}
			} catch (IOException e) {
				log.error("Could not write agents.csv.gz " + e.getMessage(), e);
			}
		}

	}

	public static String[] getColumns(String[] attributes) {
		String[] columns = new String[attributes.length + 1];
		columns[0] = "person_id";
		System.arraycopy(attributes, 0, columns, 1, attributes.length);
		return columns;
	}

}
