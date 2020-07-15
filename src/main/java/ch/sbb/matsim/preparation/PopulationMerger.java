/*
 * Copyright (C) Schweizerische Bundesbahnen SBB, 2017.
 */

package ch.sbb.matsim.preparation;

import ch.sbb.matsim.config.PopulationMergerConfigGroup;
import ch.sbb.matsim.config.variables.Filenames;
import ch.sbb.matsim.config.variables.Variables;
import java.io.File;
import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

public class PopulationMerger {

	private final static Logger log = Logger.getLogger(PopulationMerger.class);
	private Scenario scenario;
	private PopulationMergerConfigGroup config;

	public PopulationMerger(PopulationMergerConfigGroup config) {
		this.config = config;

		this.scenario = this.loadScenario(this.config.getInputPlansFiles());
	}

	public static void main(final String[] args) {
		Config config = ConfigUtils.loadConfig(args[0], new PopulationMergerConfigGroup());
		PopulationMergerConfigGroup mergerConfig = ConfigUtils.addOrGetModule(config, PopulationMergerConfigGroup.class);

		PopulationMerger merger = new PopulationMerger(mergerConfig);
		merger.run();

	}

	private Scenario loadScenario(String plansFile) {
		final Scenario scenario2 = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new PopulationReader(scenario2).readFile(plansFile);
		return scenario2;
	}

	public void run() {
		for (final String subpopulation : this.config.getPopulationTypes()) {

			final PopulationMergerConfigGroup.PopulationTypeParameterSet populationTypeParameterSet = this.config.getSubpopulations(subpopulation);

			final String planFile = populationTypeParameterSet.getPlansFile();

			log.info(subpopulation);
			log.info(planFile);
			final Scenario scenario2 = loadScenario(planFile);
			this.merge(scenario2, subpopulation);
		}

		this.write();
	}

	private void merge(final Scenario scenario2, final String subpopulation) {
		for (Person person : scenario2.getPopulation().getPersons().values()) {
			this.scenario.getPopulation().addPerson(person);
			person.getAttributes().putAttribute(Variables.SUBPOPULATION, subpopulation);
		}
	}

	protected void write() {
		final String outputFolder = this.config.getOutputFolder();
		new PopulationWriter(this.scenario.getPopulation()).write(new File(outputFolder, Filenames.PLANS).toString());
	}
}
