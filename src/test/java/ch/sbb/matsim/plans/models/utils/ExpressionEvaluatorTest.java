package ch.sbb.matsim.plans.models.utils;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpressionEvaluatorTest {

    @Test
    void testEvaluateSimpleMatrixVariable() {
        ExpressionEvaluator evaluator = new ExpressionEvaluator();
        Map<String, Double> variableData = new HashMap<>();
        variableData.put("car_travel_times", 10.0);

        double result = evaluator.evaluate("matrix_car_travel_times + 5", variableData);
        assertEquals(15.0, result, 1e-6);
    }

    @Test
    void testEvaluateNonMatrixVariable() {
        ExpressionEvaluator evaluator = new ExpressionEvaluator();
        Map<String, Double> variableData = new HashMap<>();
        variableData.put("va_impedances", 3.0);

        double result = evaluator.evaluate("va_impedances * 2", variableData);
        assertEquals(6.0, result, 1e-6);
    }

    @Test
    void testEvaluateWithMinMaxFunctions() {
        ExpressionEvaluator evaluator = new ExpressionEvaluator();
        Map<String, Double> variableData = new HashMap<>();
        variableData.put("car_travel_times", 8.0);
        variableData.put("ride_travel_times", 12.0);

        double result = evaluator.evaluate("min(matrix_car_travel_times, matrix_ride_travel_times)", variableData);
        assertEquals(8.0, result, 1e-6);

        result = evaluator.evaluate("max(matrix_car_travel_times, matrix_ride_travel_times)", variableData);
        assertEquals(12.0, result, 1e-6);
    }

    @Test
    void testEvaluateMultipleVariables() {
        ExpressionEvaluator evaluator = new ExpressionEvaluator();
        Map<String, Double> variableData = new HashMap<>();
        variableData.put("car_travel_times", 5.0);
        variableData.put("va_impedances", 2.0);

        double result = evaluator.evaluate("matrix_car_travel_times + va_impedances", variableData);
        assertEquals(7.0, result, 1e-6);
    }

    @Test
    void testBracketedVariableShortName() {
        ExpressionEvaluator evaluator = new ExpressionEvaluator();
        Map<String, Double> vars = Map.of("pop_total", 1000.0);
        assertEquals(1000.0, evaluator.evaluate("[pop_total]", vars), 1e-9);
        assertEquals(2000.0, evaluator.evaluate("2 * [pop_total]", vars), 1e-9);
    }

    @Test
    void testGetShortVariableNamesStripsBrackets() {
        ExpressionEvaluator evaluator = new ExpressionEvaluator();
        List<String> shorts = evaluator.getShortVariableNames("100 * [pop_ga] + [foo_bar]");
        assertTrue(shorts.contains("pop_ga"));
        assertTrue(shorts.contains("foo_bar"));
    }

    @Test
    void testGetRequiredVariablesMapsBracketsToShortNames() {
        ExpressionEvaluator evaluator = new ExpressionEvaluator();
        Map<String, String> req = evaluator.getRequiredVariables("[a] + [b_c] + matrix_x");
        assertEquals("a", req.get("[a]"));
        assertEquals("b_c", req.get("[b_c]"));
        assertEquals("x", req.get("matrix_x"));
    }

    @Test
    void testEqualsOperatorReturnsOneOrZero() {
        ExpressionEvaluator evaluator = new ExpressionEvaluator();
        Map<String, Double> vars = Map.of("a", 4.0, "b", 4.0, "c", 5.0);
        assertEquals(1.0, evaluator.evaluate("[a]=[b]", vars), 1e-9);
        assertEquals(0.0, evaluator.evaluate("[a]=[c]", vars), 1e-9);
    }

    @Test
    void testCachedExpressionStaysCorrectOverManyEvaluations() {
        ExpressionEvaluator evaluator = new ExpressionEvaluator();
        String formula = "100 * [x]";
        for (int i = 0; i < 1000; i++) {
            Map<String, Double> vars = Map.of("x", (double) i);
            assertEquals(100.0 * i, evaluator.evaluate(formula, vars), 1e-9);
        }
    }

    @Test
    void testParallelEvaluationIsThreadSafe() {
        ExpressionEvaluator evaluator = new ExpressionEvaluator();
        String formula = "[x] * 2 + [y]";
        long correct = java.util.stream.IntStream.range(0, 5000)
                .parallel()
                .filter(i -> {
                    Map<String, Double> vars = Map.of("x", (double) i, "y", 1.0);
                    return evaluator.evaluate(formula, vars) == 2.0 * i + 1.0;
                })
                .count();
        assertEquals(5000, correct);
    }
}