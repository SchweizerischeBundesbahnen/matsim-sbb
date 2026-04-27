package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.config.variables.SBBActivities;
import ch.sbb.matsim.config.variables.Variables;
import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.constants.ZonalAttributes;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.zones.AttributableZone;
import ch.sbb.matsim.zones.Zone;
import ch.sbb.matsim.zones.Zones;
import ch.sbb.matsim.zones.ZonesImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RailaccessChoiceSetTest {

    @TempDir
    Path tempDir;

    private Scenario scenario;
    private ZonesImpl zones;
    private MOBiPlansConfig mobiPlansConfig;
    private Person person;

    @BeforeEach
    void setUp() throws IOException {
        // 1. Scenario & Population Setup
        scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        person = scenario.getPopulation().getFactory().createPerson(Id.createPersonId("agent_1"));

        // Age 30 falls into pop_2544 (the 3rd bucket), so age_cat should be 3
        person.getAttributes().putAttribute(SynpopConstants.AGE, 30);
        scenario.getPopulation().addPerson(person);

        // 2. Zone Setup
        zones = new ZonesImpl(Id.create("zones", Zones.class));
        scenario.addScenarioElement(Variables.ZONES, zones);

        Zone testZone = new AttributableZone(Id.create("z1", Zone.class), null);
        testZone.getAttributes().putAttribute(ZonalAttributes.MOTORISATION_LEVEL, 1.75);
        testZone.getAttributes().putAttribute(ZonalAttributes.SL3_ID, 2);
        zones.add(testZone);

        // Assign person to the zone
        person.getAttributes().putAttribute(SynpopConstants.ZONE, testZone);

        // 3. Config Setup
        MOBiPlansConfig.DataConfig dataConfig = new MOBiPlansConfig.DataConfig(
                List.of(), null, null, null, null, null, null, null,
                tempDir.toString(), null, null, null, null, null
        );
        MOBiPlansConfig.ModelsConfig modelsConfig = new MOBiPlansConfig.ModelsConfig(
                1, 1.0, false, "test_run", null,null,null
        );
        mobiPlansConfig = new MOBiPlansConfig(dataConfig, modelsConfig);

        // 4. Fake CSV File Setup
        createFakeCsv();
    }

    private void createFakeCsv() throws IOException {
        Path choiceModelsDir = tempDir.resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_CHOICE_MODELS);
        Files.createDirectories(choiceModelsDir);

        // Create a deterministically rigged CSV:
        // - Main Logit Model: beta_1 = 100.0, beta_0 = 0.0 -> P(1) ≈ 100%
        // - Activity 'work': beta_1 = 1.0 -> 100% probability
        // - Activity 'leisure': beta_1 = 1.0 -> 100% probability
        // - Activity 'shopping': beta_1 = 0.0 -> 0% probability

        String csvContent = "railaccess_attribute;segment;filter_expression;type;attribute;values;lambda;beta_0;beta_1;comment\n" +
                "car;non_rural;age>=18;constant;;;0;0.0;100.0;Force main model to 1\n" +
                "car;non_rural;age>=18;activity;work;;0;0.0;1.0;Always work\n" +
                "car;non_rural;age>=18;activity;leisure;;0;0.0;1.0;Always leisure\n" +
                "car;non_rural;age>=18;activity;shopping;;0;0.0;0.0;Never shopping\n";

        Files.writeString(choiceModelsDir.resolve(MOBiPlansConfig.RAILACCESS_CHOICESET_CSV), csvContent);
    }

    @Test
    void testRailaccessChoiceSet_CalculatesVariablesAndModelsCorrectly() {
        // 1. Act
        RailaccessChoiceSet model = new RailaccessChoiceSet(scenario, mobiPlansConfig, zones);
        model.run();

        // 2. Assert Extra Variables (Age Cat & Zonal Attributes)
        assertEquals("3", person.getAttributes().getAttribute(RailaccessChoiceSet.AGE_CAT),
                "Age 30 should map to age_cat 3 (pop_2544)");

        assertEquals(1.75, (Double) person.getAttributes().getAttribute(ZonalAttributes.MOTORISATION_LEVEL), 0.001,
                "Motorisation level should be copied from zone");

        assertEquals(2, (Integer) person.getAttributes().getAttribute(ZonalAttributes.SL3_ID),
                "SL3_ID should be copied from zone");

        // 3. Assert Main Access Model Result
        // The segment attribute is 'car' and the suffix is '2pt', so the output attribute is 'car2pt'
        String accessAvailable = (String) person.getAttributes().getAttribute("car2pt");
        assertEquals("1", accessAvailable, "Logit model should evaluate to 1 due to beta=100.0");

        // 4. Assert Activity-Specific Probabilities String
        String actAccessString = (String) person.getAttributes().getAttribute("car2pt_act");
        assertNotNull(actAccessString, "Activity string should be populated");

        // Because we forced work=1.0, leisure=1.0, and shopping=0.0, the string should contain work and leisure, but NOT shopping.
        // (Note: other activities not mentioned in the CSV default to true in the code logic!)
        assertTrue(actAccessString.contains(SBBActivities.work), "String should contain 'work'");
        assertTrue(actAccessString.contains(SBBActivities.leisure), "String should contain 'leisure'");
        assertFalse(actAccessString.contains(SBBActivities.shopping), "String should NOT contain 'shopping'");

        // Assert the string is properly comma-separated without leading/trailing commas
        assertFalse(actAccessString.startsWith(","));
        assertFalse(actAccessString.endsWith(","));
        assertFalse(actAccessString.contains(",,"));
    }
}