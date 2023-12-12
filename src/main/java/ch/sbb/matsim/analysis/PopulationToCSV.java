/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.analysis;

import ch.sbb.matsim.config.PostProcessingConfigGroup;
import ch.sbb.matsim.csv.CSVWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;

import java.io.IOException;

public class PopulationToCSV {

	private final static Logger log = LogManager.getLogger(PopulationToCSV.class);

	private final Scenario scenario;

	public PopulationToCSV(Scenario scenario) {
		this.scenario = scenario;
	}

	public void write(String directory) {
		String agentsFilename = directory + "agents.csv.gz";
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
