package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.config.variables.SBBActivities;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.scenario.ScenarioUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecondaryActivityTypeAssignmentModelTest {

    @TempDir
    Path tempDir; // JUnit 5 automatically creates and cleans up this directory

    private Person person;
    private FixedRandom fakeRandom;
    private SecondaryActivityTypeAssignmentModel model;

    /**
     * A simple, predictable Random subclass for testing roulette wheel selection.
     */
    private static class FixedRandom extends Random {
        private double nextDoubleValue = 0.5;

        public void setNextDouble(double value) {
            this.nextDoubleValue = value;
        }

        @Override
        public double nextDouble() {
            return nextDoubleValue;
        }
    }

    @BeforeEach
    void setUp() throws IOException {
        // 1. Create a real MATSim Person
        person = ScenarioUtils.createScenario(ConfigUtils.createConfig())
                .getPopulation().getFactory().createPerson(Id.createPersonId("test_person"));

        // 2. Setup our controllable random generator
        fakeRandom = new FixedRandom();

        // 3. Create a temporary CSV file with targeted test rules
        Path csvPath = tempDir.resolve("test_secondary_activities.csv");

        // We use SBBActivities directly to ensure the test header matches the model's expected array
        String header = String.format("comment;filter_expression;%s;%s;%s;%s;%s;%s;%s\n",
                SBBActivities.accompany, SBBActivities.business, SBBActivities.education,
                SBBActivities.education_continued, SBBActivities.leisure, SBBActivities.other, SBBActivities.shopping);

        String csvContent = header +
                // Rule 1: Age < 18 -> 100% accompany
                "child; age<18 ;1.0;0.0;0.0;0.0;0.0;0.0;0.0\n" +

                // Rule 2: Multiple conditions & Boolean conversion (has_child==0) -> 100% business
                "worker; age>=18 & employed==1 & has_child==0 ;0.0;1.0;0.0;0.0;0.0;0.0;0.0\n" +

                // Rule 3: Probabilistic split (50% leisure, 50% shopping)
                "mixed; age==30 ;0.0;0.0;0.0;0.0;0.5;0.0;0.5\n" +

                // Rule 4: String parsed as double
                "income_check; income<=50.5 ;0.0;0.0;1.0;0.0;0.0;0.0;0.0\n" +

                // Metadata/Garbage row to test graceful skipping
                "Value; 6; 1; 2; 8; 4; 7; 5\n";

        Files.writeString(csvPath, csvContent);

        // 4. Initialize the model with the temporary file
        model = new SecondaryActivityTypeAssignmentModel(csvPath.toString());
    }

    @Test
    void testRule1_MatchesSingleCondition() {
        person.getAttributes().putAttribute("age", 15);

        // Random shouldn't matter because probability is 1.0
        fakeRandom.setNextDouble(0.5);

        String assignedType = model.assignActivityType(person, fakeRandom);
        assertEquals(SBBActivities.accompany, assignedType);
    }

    @Test
    void testRule2_MatchesMultipleConditionsAndBooleanConversion() {
        person.getAttributes().putAttribute("age", 25);
        person.getAttributes().putAttribute("employed", 1);

        // Model should convert Boolean false to 0.0
        person.getAttributes().putAttribute("has_child", false);

        fakeRandom.setNextDouble(0.1);

        String assignedType = model.assignActivityType(person, fakeRandom);
        assertEquals(SBBActivities.business, assignedType);
    }

    @Test
    void testRule3_ProbabilisticSelection() {
        person.getAttributes().putAttribute("age", 30);

        // Test first half of the probability distribution (0.5 leisure)
        fakeRandom.setNextDouble(0.2); // 0.2 < 0.5
        String assignedType1 = model.assignActivityType(person, fakeRandom);
        assertEquals(SBBActivities.leisure, assignedType1);

        // Test second half of the probability distribution (0.5 shopping)
        fakeRandom.setNextDouble(0.8); // 0.8 > 0.5
        String assignedType2 = model.assignActivityType(person, fakeRandom);
        assertEquals(SBBActivities.shopping, assignedType2);
    }

    @Test
    void testRule4_StringAttributeParsedAsDouble() {
        // Put the attribute in as a String instead of a Number
        person.getAttributes().putAttribute("income", "45.0");

        fakeRandom.setNextDouble(0.5);

        String assignedType = model.assignActivityType(person, fakeRandom);
        assertEquals(SBBActivities.education, assignedType); // Income <= 50.5 gives education in our test CSV
    }

    @Test
    void testNoMatchDefaultsToOther() {
        // Doesn't match any rule (age is 40, not 30, >=18 but not employed, etc.)
        person.getAttributes().putAttribute("age", 40);
        person.getAttributes().putAttribute("employed", 0);

        String assignedType = model.assignActivityType(person, fakeRandom);
        assertEquals("other", assignedType);
    }

    @Test
    void testMissingAttributeDefaultsToOther() {
        // We supply no attributes at all. Every rule will return false on evaluation.
        String assignedType = model.assignActivityType(person, fakeRandom);
        assertEquals("other", assignedType);
    }
}