package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.projects.synpop.SingleTripAgentToCSVConverter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.population.io.PopulationWriter;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MergeExogenousPlansTest {

    @TempDir
    Path tempDir;

    private Scenario mainScenario;
    private MOBiPlansConfig config;

    @BeforeEach
    void setUp() throws IOException {
        mainScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());

        // Add one existing person to the main scenario
        Person existingPerson = mainScenario.getPopulation().getFactory().createPerson(Id.createPersonId("agent_existing"));
        mainScenario.getPopulation().addPerson(existingPerson);


        Path exogenousFolder = tempDir.resolve("exo_plans");
        Files.createDirectories(exogenousFolder);

        MOBiPlansConfig.DataConfig dataConfig = new MOBiPlansConfig.DataConfig(
                List.of(), null, null, null, null, null, null, null, null, null, null, null, null, null,exogenousFolder.toString(),
                null);
        // Force the config to return our temp directory using an anonymous subclass or mock (if records allow)
        // Since records are immutable, we just map it. Assuming exogenousPlansFolder() is exposed in your config.
        config = new MOBiPlansConfig(dataConfig,new MOBiPlansConfig.ModelsConfig(null,null,null,null,null,null,null));
    }

    @Test
    void testMergeExogenousPlans_MergesValidXmlAndCsv() throws IOException {
        Path exoFolder = Path.of(config.data().exogenousPlansFolder());

        // 1. Create a valid XML Population file
        Scenario xmlScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Person xmlPerson = xmlScenario.getPopulation().getFactory().createPerson(Id.createPersonId("exo_xml_1"));
        PopulationUtils.putSubpopulation(xmlPerson, "freight");
        xmlScenario.getPopulation().addPerson(xmlPerson);
        new PopulationWriter(xmlScenario.getPopulation()).write(exoFolder.resolve("freight.xml").toString());

        // 2. Create a valid CSV file
        String csvContent = String.join(";", SingleTripAgentToCSVConverter.COLUMNS) + "\n" +
                "exo_csv_1;border_traffic;home;100.0;200.0;3600.0;car;work;300.0;400.0;inbound;work\n";
        Files.writeString(exoFolder.resolve("border.csv"), csvContent);

        // 3. Create an INVALID XML (Duplicate ID)
        Scenario badScenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        Person badPerson = badScenario.getPopulation().getFactory().createPerson(Id.createPersonId("agent_existing")); // Duplicate!
        PopulationUtils.putSubpopulation(badPerson, "freight");
        badScenario.getPopulation().addPerson(badPerson);
        new PopulationWriter(badScenario.getPopulation()).write(exoFolder.resolve("bad_duplicate.xml").toString());

        // 4. Run the Merger
        MergeExogenousPlans merger = new MergeExogenousPlans(config, mainScenario);
        merger.run();

        // 5. Assertions
        // The main scenario should now have exactly 3 people: the original one, the XML one, and the CSV one.
        // The duplicate XML should have been gracefully skipped.
        assertEquals(3, mainScenario.getPopulation().getPersons().size());

        assertTrue(mainScenario.getPopulation().getPersons().containsKey(Id.createPersonId("exo_xml_1")));
        assertTrue(mainScenario.getPopulation().getPersons().containsKey(Id.createPersonId("exo_csv_1")));

        // Check that slices were assigned
        Person mergedPerson = mainScenario.getPopulation().getPersons().get(Id.createPersonId("exo_xml_1"));
        assertNotNull(mergedPerson.getAttributes().getAttribute(Variables.SLICE));
    }
}