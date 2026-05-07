package ch.sbb.matsim.plans.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link LookupFormulaParser}.
 */
public class LookupFormulaParserTest {

    @Test
    public void parsesSimpleFormulaWithoutAggregator() {
        LookupFormulaParser.ParseResult r = LookupFormulaParser.parseFormula("100 * [pop_ga]");
        assertNull(r.aggregator);
        assertNull(r.lookupTable);
        assertNull(r.lookupExpression);
        assertNull(r.filterExpression);
        // The whole formula is the inner expression when there is no aggregator
        assertEquals("100 * [pop_ga]", r.innerExpression);
        assertTrue(r.attributes.contains("pop_ga"));
    }

    @Test
    public void parsesAggregatorWithTripsLookup() {
        LookupFormulaParser.ParseResult r = LookupFormulaParser.parseFormula("Sum(Trips->[weight])");
        assertEquals("Sum", r.aggregator);
        assertEquals("Trips", r.lookupTable);
        // bracket is part of lookupExpression (without normalization step removing it)
        assertTrue(r.lookupExpression.contains("weight"));
        assertNull(r.filterExpression);
    }

    @Test
    public void parsesAggregatorWithFilterExpression() {
        String formula = "Sum(Trips->[weight], [originActivity/type]='work' & [destinationActivity/type]='home')";
        LookupFormulaParser.ParseResult r = LookupFormulaParser.parseFormula(formula);
        assertEquals("Sum", r.aggregator);
        assertEquals("Trips", r.lookupTable);
        assertNotNull(r.filterExpression);
        // brackets are stripped from the filter expression
        assertFalse(r.filterExpression.contains("["));
        assertFalse(r.filterExpression.contains("]"));
        assertTrue(r.filterExpression.contains("originActivity/type"));
        assertTrue(r.filterExpression.contains("destinationActivity/type"));
    }

    @Test
    public void parsesCountAggregator() {
        LookupFormulaParser.ParseResult r = LookupFormulaParser.parseFormula("Count(Trips->[weight])");
        assertEquals("Count", r.aggregator);
        assertEquals("Trips", r.lookupTable);
    }

    @Test
    public void collectsAttributesFromAcrossFormula() {
        LookupFormulaParser.ParseResult r = LookupFormulaParser.parseFormula(
                "Sum(Trips->[distance], [origin/type]='work' & [dest/type]='home')");
        assertTrue(r.attributes.contains("distance"));
        assertTrue(r.attributes.contains("origin/type"));
        assertTrue(r.attributes.contains("dest/type"));
    }

    @Test
    public void normalizeToLowerRightLowercasesEachSegment() {
        assertEquals("originActivity/type",
                LookupFormulaParser.normalizeToLowerRight("OriginActivity/Type"));
        assertEquals("a/b/c", LookupFormulaParser.normalizeToLowerRight("A/B/C"));
        assertEquals("foo", LookupFormulaParser.normalizeToLowerRight("Foo"));
    }

    @Test
    public void nullFormulaThrows() {
        assertThrows(IllegalArgumentException.class, () -> LookupFormulaParser.parseFormula(null));
    }

    @Test
    public void commaInsideBracketsIsNotTopLevel() {
        // a comma inside [] should not be treated as the filter delimiter
        LookupFormulaParser.ParseResult r = LookupFormulaParser.parseFormula(
                "Sum(Trips->[weight,with,commas])");
        assertEquals("Sum", r.aggregator);
        assertEquals("Trips", r.lookupTable);
        assertNull(r.filterExpression);
    }
}

