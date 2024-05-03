package ch.sbb.matsim.preparation.casestudies;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.io.PopulationReader;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.util.concurrent.ThreadLocalRandom;

public class AddSlice {
	// adds attribute "slice" to existing population

	String pathPlans;
	String pathPlansOut;
	Population population = null;

	public AddSlice(String pathPlans, String pathPlansOut) {
		this.pathPlans = pathPlans;
		this.pathPlansOut = pathPlansOut;
	}


	public static void main(String[] args) {
		AddSlice addSlice = new AddSlice(args[0], args[1]);
		addSlice.readPlans();
		addSlice.writePlans();
	}

	public void readPlans() {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		new PopulationReader(scenario).readFile(this.pathPlans);
		this.population = scenario.getPopulation();
	}

	private void writePlans() {
		Scenario scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
		Population outPopulation = scenario.getPopulation();
		for (Person p : this.population.getPersons().values()) {
			p.getAttributes().putAttribute("slice", ThreadLocalRandom.current().nextInt(1, 21));
			outPopulation.addPerson(p);
		}
		new PopulationWriter(outPopulation).write(this.pathPlansOut);
	}
}

