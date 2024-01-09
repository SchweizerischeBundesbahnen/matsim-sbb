package ch.sbb.matsim.analysis;

import ch.sbb.matsim.config.PostProcessingConfigGroup;
import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Population;
import org.matsim.api.core.v01.population.PopulationFactory;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PersonUtils;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.testcases.MatsimTestUtils;

import java.io.BufferedReader;
import java.io.IOException;

public class PopulationToCSVTest {
	@RegisterExtension
	public final MatsimTestUtils utils = new MatsimTestUtils();

	@Test
	public final void testPopulationPostProc() throws IOException {

		PostProcessingConfigGroup pg = new PostProcessingConfigGroup();
		pg.setPersonAttributes("carAvail,hasLicense,sex,subpopulation,age");
		pg.setWriteAgentsCSV(true);

		Config config = ConfigUtils.createConfig(pg);
		Scenario scenario = ScenarioUtils.createScenario(config);
		Population population = scenario.getPopulation();
		PopulationFactory populationFactory = population.getFactory();

		Person person = populationFactory.createPerson(Id.createPersonId("1"));

		PersonUtils.setCarAvail(person, "never");
		PersonUtils.setLicence(person, "driving");

		PersonUtils.setAge(person, 1);
		//PersonUtils.setEmployed(person, true);
		PersonUtils.setSex(person, "m");

		person.getAttributes().putAttribute("subpopulation", "regular");

		population.addPerson(person);

		String filename = this.utils.getOutputDirectory();
		PopulationToCSV tool = new PopulationToCSV(scenario);
		tool.write(filename);

		try (BufferedReader reader = IOUtils.getBufferedReader(filename + "agents.csv")) {
			String headerLine = reader.readLine();
			Assert.assertEquals("person_id;carAvail;hasLicense;sex;subpopulation;age", headerLine);
			String firstRow = reader.readLine();
			Assert.assertEquals("1;never;driving;m;regular;1", firstRow);
		}
	}

}
