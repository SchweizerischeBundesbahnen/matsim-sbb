package ch.sbb.matsim.plans.models.steps.starttimechoice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.core.population.PopulationUtils;
import org.matsim.utils.objectattributes.attributable.Attributes;

import java.util.function.BiPredicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeSeriesPredicateParserTest {


    private Attributes attributes;
    private Person person;
    @BeforeEach
    void setUp() {
        person = PopulationUtils.getFactory().createPerson(Id.createPersonId("test_person"));
        attributes = person.getAttributes();
    }

    @Test
    void testEmptyExpressionReturnsTrue() {
        BiPredicate<Person, Double> predicate = TimeSeriesPredicateParser.parse("");
        assertTrue(predicate.test(person, 0.0), "Empty expression should always return true.");

        BiPredicate<Person, Double> nullPredicate = TimeSeriesPredicateParser.parse(null);
        assertTrue(nullPredicate.test(person, 0.0), "Null expression should always return true.");
    }

    @Test
    void testSimpleAttributeConditions() {
        attributes.putAttribute("age", 45);

        // Test equality (both = and == should work)
        assertTrue(TimeSeriesPredicateParser.parse("age=45").test(person, 0.0));
        assertTrue(TimeSeriesPredicateParser.parse("age==45").test(person, 0.0));
        assertFalse(TimeSeriesPredicateParser.parse("age=50").test(person, 0.0));

        // Test inequalities
        assertTrue(TimeSeriesPredicateParser.parse("age<50").test(person, 0.0));
        assertTrue(TimeSeriesPredicateParser.parse("age<=45").test(person, 0.0));
        assertFalse(TimeSeriesPredicateParser.parse("age>50").test(person, 0.0));
        assertTrue(TimeSeriesPredicateParser.parse("age!=50").test(person, 0.0));
    }

    @Test
    void testTripDistanceCondition() {
        // Distance is 4000 meters
        double tripDistance = 4000.0;

        assertTrue(TimeSeriesPredicateParser.parse("trip.distance=4000").test(person, tripDistance));
        assertTrue(TimeSeriesPredicateParser.parse("trip.distance<=5000").test(person, tripDistance));
        assertFalse(TimeSeriesPredicateParser.parse("trip.distance>5000").test(person, tripDistance));
    }

    @Test
    void testAndOrLogic() {
        attributes.putAttribute("age", 30);
        attributes.putAttribute("employed", 1);

        // AND logic
        assertTrue(TimeSeriesPredicateParser.parse("age>20 & employed=1").test(person, 0.0));
        assertFalse(TimeSeriesPredicateParser.parse("age>20 & employed=0").test(person, 0.0));

        // OR logic
        assertTrue(TimeSeriesPredicateParser.parse("age>40 | employed=1").test(person, 0.0));
        assertFalse(TimeSeriesPredicateParser.parse("age>40 | employed=0").test(person, 0.0));
    }

    @Test
    void testComplexNestedBrackets() {
        // Testing the actual SBB rule: trip.distance<4000 & (age<65 | (age>=65 & level_of_employment>=20))
        String rule = "trip.distance<4000 & (age<65 | (age>=65 & level_of_employment>=20))";
        BiPredicate<Person, Double> complexPredicate = TimeSeriesPredicateParser.parse(rule);

        // Case 1: Distance is short, Age is young -> TRUE (Bypasses employment check)
        attributes.putAttribute("age", 30);
        attributes.putAttribute("level_of_employment", 0);
        assertTrue(complexPredicate.test(person, 3000.0));

        // Case 2: Distance is too long -> FALSE
        assertFalse(complexPredicate.test(person, 5000.0));

        // Case 3: Distance is short, Age is old, but low employment -> FALSE
        attributes.putAttribute("age", 70);
        attributes.putAttribute("level_of_employment", 10);
        assertFalse(complexPredicate.test(person, 3000.0));

        // Case 4: Distance is short, Age is old, and high employment -> TRUE
        attributes.putAttribute("level_of_employment", 50);
        assertTrue(complexPredicate.test(person, 3000.0));
    }

    @Test
    void testBooleanAttributeConversion() {
        // MATSim often stores booleans. The parser should convert them to 1.0 or 0.0 automatically.
        attributes.putAttribute("has_child_in_hh", true);

        assertTrue(TimeSeriesPredicateParser.parse("has_child_in_hh=1").test(person, 0.0));
        assertFalse(TimeSeriesPredicateParser.parse("has_child_in_hh=0").test(person, 0.0));

        attributes.putAttribute("has_child_in_hh", false);
        assertTrue(TimeSeriesPredicateParser.parse("has_child_in_hh=0").test(person, 0.0));
    }

    @Test
    void testMissingAttributeSafelyFails() {
        // Person does not have an "income" attribute
        BiPredicate<Person, Double> predicate = TimeSeriesPredicateParser.parse("income>5000");

        // Should return false safely without throwing NullPointerException
        assertFalse(predicate.test(person, 0.0));
    }

    @Test
    void testMalformedSyntaxGracefulFailure() {
        // 1. Missing closing bracket
        BiPredicate<Person, Double> badBrackets = TimeSeriesPredicateParser.parse("age<65 | (age>65");
        assertFalse(badBrackets.test(person, 0.0), "Malformed brackets should safely return false.");

        // 2. Gibberish operator
        BiPredicate<Person, Double> badOperator = TimeSeriesPredicateParser.parse("age >> 65");
        assertFalse(badOperator.test(person, 0.0), "Malformed operator should safely return false.");

        // 3. Trailing logic gate
        BiPredicate<Person, Double> trailingAnd = TimeSeriesPredicateParser.parse("age<65 & ");
        assertFalse(trailingAnd.test(person, 0.0), "Trailing operators should safely return false.");
    }

    @Test
    void testSpacesAreIgnored() {
        attributes.putAttribute("age", 30);

        // Ensure whitespace doesn't break the regex
        assertTrue(TimeSeriesPredicateParser.parse("age = 30").test(person, 0.0));
        assertTrue(TimeSeriesPredicateParser.parse("  age   <   40  ").test(person, 0.0));
    }
}