package ch.sbb.matsim.plans.models.steps.starttimechoice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.population.PopulationUtils;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StartTimeChoiceConfigParserTest {

    @TempDir
    Path tempDir;

    private Person person;
    private Attributes attributes;

    @BeforeEach
    void setUp() {
        person = PopulationUtils.getFactory().createPerson(Id.createPersonId("test_person"));
        attributes = person.getAttributes();
    }

    @Test
    void testParseCsvWithDynamicTimeBins() throws IOException {
        // 1. Prepare dummy CSV data mimicking your exact headers
        Path csvPath = tempDir.resolve("start_times.csv");
        String csvContent =
                "comment;activity;attribute_expression;exp_beta;TIMESERIESNO;0_3599;3600_7199;7200_10799\n" +
                        "A empl has_child;accompany;has_child_in_hh=1;1.0;101;0.1;0.4;0.5\n" +
                        "A u65 no_child;accompany;has_child_in_hh=0 & age<65;1.5;111;0.0;0.2;0.8\n" +
                        "LS u65;leisure;trip.distance<4000;2.0;311;0.5;0.5;0.0\n";

        Files.writeString(csvPath, csvContent);

        // 2. Parse the file
        Map<String, List<StartTimeChoiceModel.TimeSeriesRule>> rulesByActivity = StartTimeChoiceConfigParser.parse(csvPath.toString());

        // 3. Assert map structure
        assertEquals(2, rulesByActivity.size(), "Should parse rules for 2 distinct activities (accompany, leisure)");
        assertTrue(rulesByActivity.containsKey("accompany"));
        assertTrue(rulesByActivity.containsKey("leisure"));

        // 4. Assert specific rules for 'accompany'
        List<StartTimeChoiceModel.TimeSeriesRule> accompanyRules = rulesByActivity.get("accompany");
        assertEquals(2, accompanyRules.size());

        // Check the first accompany rule properties
        StartTimeChoiceModel.TimeSeriesRule rule1 = accompanyRules.get(0);
        assertEquals("accompany", rule1.activityType());
        assertEquals("has_child_in_hh=1", rule1.attributeExpression());
        assertEquals(1.0, rule1.expBeta());

        // 5. Check dynamic Time Bins parsing
        List<StartTimeChoiceModel.TimeBin> bins = rule1.timeBins();
        assertEquals(3, bins.size(), "Should have exactly 3 time bins based on the header");

        // Verify Bin 1 (0_3599)
        assertEquals(0, bins.get(0).startTimeSec());
        assertEquals(3599, bins.get(0).endTimeSec());
        assertEquals(0.1, bins.get(0).probability());
        assertEquals(3599, bins.get(0).getDurationSec(), "Duration calculation should work");

        // Verify Bin 3 (7200_10799)
        assertEquals(7200, bins.get(2).startTimeSec());
        assertEquals(10799, bins.get(2).endTimeSec());
        assertEquals(0.5, bins.get(2).probability());

        // 6. Test the compiled BiPredicate (Logic evaluation)
        // Setup person as having a child
        attributes.putAttribute("has_child_in_hh", 1);
        assertTrue(rule1.condition().test(person, 0.0), "Rule 1 should match person with child");

        // Setup person as no child, young
        attributes.putAttribute("has_child_in_hh", 0);
        attributes.putAttribute("age", 30);
        assertFalse(rule1.condition().test(person, 0.0), "Rule 1 should fail for no child");
        assertTrue(accompanyRules.get(1).condition().test(person, 0.0), "Rule 2 should match no child & age < 65");

        // 7. Test 'leisure' rule to ensure trip distance context parameter works
        List<StartTimeChoiceModel.TimeSeriesRule> leisureRules = rulesByActivity.get("leisure");
        assertEquals(1, leisureRules.size());
        StartTimeChoiceModel.TimeSeriesRule leisureRule = leisureRules.get(0);

        assertTrue(leisureRule.condition().test(person, 3000.0), "Should match trip distance < 4000");
        assertFalse(leisureRule.condition().test(person, 5000.0), "Should fail trip distance >= 4000");
    }
}