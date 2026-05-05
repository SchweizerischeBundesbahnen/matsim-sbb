package ch.sbb.matsim.plans.models.steps.csvmodels;

import ch.sbb.matsim.plans.models.steps.ChoiceModel;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.utils.objectattributes.attributable.Attributable;
import org.matsim.utils.objectattributes.attributable.Attributes;
import org.matsim.utils.objectattributes.attributable.AttributesImpl;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ASCFittingToTargetsTest {

    /**
     * A lightweight dummy object that satisfies both Attributable and Identifiable<?>
     */
    private static class DummyAgent implements Attributable, Identifiable<DummyAgent> {
        private final Id<DummyAgent> id;
        private final Attributes attributes = new AttributesImpl();

        DummyAgent(int idNum) {
            this.id = Id.create(idNum, DummyAgent.class);
        }

        @Override public Id<DummyAgent> getId() { return id; }
        @Override public Attributes getAttributes() { return attributes; }
    }

    @Test
    void testFitAndAssignTarget_AccuratelyHitsTarget() {
        // 1. Create a population of 1000 agents
        int populationSize = 1000;
        List<DummyAgent> agents = new ArrayList<>();
        for (int i = 0; i < populationSize; i++) {
            agents.add(new DummyAgent(i));
        }

        // 2. Create a dummy ChoiceModel where everyone naturally has only a 10% chance
        ChoiceModel dummyModel = new ChoiceModel() {
            @Override
            public Object calculate(Attributable attributable) {
                return null;
            }

            @Override
            public double getProbabilityForAlternative(Attributable attributable, String alternativeName) {
                return 0.10; // 10% base probability
            }
        };

        // 3. Set a macro-target of 600 (60% of the population)
        int targetCount = 600;
        String attributeToSet = "is_working_from_home";

        // 4. Run the Fitter!
        ASCFittingToTargets.fitAndAssignTarget(agents, targetCount, dummyModel, attributeToSet, "1");

        // 5. Count the results
        long actualCount = agents.stream()
                .filter(a -> Boolean.TRUE.equals(a.getAttributes().getAttribute(attributeToSet)))
                .count();

        // 6. Assertions
        // Because of the random roulette wheel at the end, the exact number might be 598 or 603,
        // but it should be extremely close to the mathematical target of 600 (within a ~2.5% margin of error).
        long errorMargin = 25;

        assertTrue(actualCount >= targetCount - errorMargin && actualCount <= targetCount + errorMargin,
                "Expected around " + targetCount + " agents, but got " + actualCount + ". The Newton-Raphson ASC shift failed.");
    }

    @Test
    void testFitAndAssignTarget_EdgeCases() {
        List<DummyAgent> agents = List.of(new DummyAgent(1), new DummyAgent(2));

        ChoiceModel dummyModel = new ChoiceModel() {;
            @Override
            public Object calculate(Attributable attributable) {
                return null;
            }

            @Override
            public double getProbabilityForAlternative(Attributable attributable, String alternativeName) {
                return 0.5; // 50% base probability for simplicity
            }
        };

        // Test Target = 0 (Should force all to false immediately)
        ASCFittingToTargets.fitAndAssignTarget(agents, 0, dummyModel, "test_attr", "1");
        assertEquals(false, agents.get(0).getAttributes().getAttribute("test_attr"));
        assertEquals(false, agents.get(1).getAttributes().getAttribute("test_attr"));

        // Test Target > Population (Should force all to true immediately)
        ASCFittingToTargets.fitAndAssignTarget(agents, 500, dummyModel, "test_attr", "1");
        assertEquals(true, agents.get(0).getAttributes().getAttribute("test_attr"));
        assertEquals(true, agents.get(1).getAttributes().getAttribute("test_attr"));
    }
}