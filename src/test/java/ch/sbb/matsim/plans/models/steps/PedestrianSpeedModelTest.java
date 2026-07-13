package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.plans.constants.SynpopConstants;
import ch.sbb.matsim.plans.models.config.MOBiPlansConfig;
import ch.sbb.matsim.zones.AttributableZone;
import ch.sbb.matsim.zones.Zone;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PedestrianSpeedModelTest {

    @TempDir
    Path tempDir;

    private Scenario scenario;
    private Person person;
    private MOBiPlansConfig mobiPlansConfig;

    @BeforeEach
    void setUp() throws IOException {
        // 1. Scenario Setup
        scenario = ScenarioUtils.createScenario(ConfigUtils.createConfig());
        person = scenario.getPopulation().getFactory().createPerson(Id.createPersonId("agent_1"));

        // Setup Person Attributes
        person.getAttributes().putAttribute(SynpopConstants.ACTIVE_IN_MODEL, true);
        person.getAttributes().putAttribute("age", 40);

        // Setup nested Zone attributes so "amgr_id" evaluates correctly!
        Zone dummyZone = new AttributableZone(Id.create("z1", Zone.class), null);
        dummyZone.getAttributes().putAttribute("amgr_id", 6);
        dummyZone.getAttributes().putAttribute("sl3_id", 1);
        person.getAttributes().putAttribute("zone", dummyZone);

        scenario.getPopulation().addPerson(person);

        // 2. CSV Generation
        tempDir.resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_CHOICE_MODELS).toFile().mkdirs();
        Path csvFile = tempDir.resolve(MOBiPlansConfig.MODEL_BASE_FOLDER_CHOICE_MODELS).resolve("pedestrian_speed.csv");
        String csvContent = """
                type;attribute;values;lambda;value;parameter_name
                constant;;;;4.272817801;const
                linear;age;;;-0.012615335;alter
                condition;zone.amgr_id;==6;;0.18746851;home_amgr_bern
                condition;zone.amgr_id;==12;;0.130476115;home_amgr_zurich
                condition;zone.sl3_id;==1;;0.105214335;home_urban
                """;
        Files.writeString(csvFile, csvContent);

        // 3. Config Setup
        MOBiPlansConfig.DataConfig dataConfig = new MOBiPlansConfig.DataConfig(
                List.of(), null, null, null, null, null, null, null,
                tempDir.toString(), null, null, null, null, null,null
        );
        MOBiPlansConfig.ModelsConfig modelsConfig = new MOBiPlansConfig.ModelsConfig(
                1, 1.0, false, "test_run", null,null,null
        );
        mobiPlansConfig = new MOBiPlansConfig(dataConfig, modelsConfig);

    }

    @Test
    void testPedestrianSpeedModel_AssignsAttributeToPerson() {
        // 1. Run the high-level ModelStep
        PedestrianSpeedModel model = new PedestrianSpeedModel(scenario, mobiPlansConfig);
        model.run();

        // 2. Verify it attached to the Person
        Object speedAttr = person.getAttributes().getAttribute(SynpopConstants.PEDESTRIAN_SPEED);
        assertNotNull(speedAttr, "Pedestrian speed should be assigned to the person.");

        double speed = (double) speedAttr;
        double expectedSpeed = (4.272817801 + (40 * -0.012615335) + 0.18746851 + 0.105214335)/3.6;

        assertEquals(expectedSpeed, speed, 0.000001);
    }
}