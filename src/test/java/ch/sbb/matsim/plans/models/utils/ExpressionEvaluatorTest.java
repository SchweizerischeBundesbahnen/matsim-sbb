package ch.sbb.matsim.plans.models.utils;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}