package ch.sbb.matsim.plans.models.utils;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.function.Function;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExpressionEvaluator {
    // Custom functions are defined once and reused.
    private static final Function functionMin = new Function("min", 2) {
        @Override
        public double apply(double... args) {
            return Math.min(args[0], args[1]);
        }
    };
    private static final Function functionMax = new Function("max", 2) {
        @Override
        public double apply(double... args) {
            return Math.max(args[0], args[1]);
        }
    };

    /**
     * Evaluates a given formula string with a map of variable data.
     *
     * @param formulaString The raw formula (e.g., "-1.5+(-0.038)*matrix_22+...").
     * @param variableData  A map of variable names to their double values.
     *                      For matrix variables, this map should use the short name
     *                      (e.g., "pt_travel_times_local" instead of "matrix_pt_travel_times_local").
     * @return The calculated result.
     */
    public double evaluate(String formulaString, Map<String, Double> variableData) {
        // 1. Create a modified formula where "matrix_variableName" is replaced by "variableName".
        String modifiedFormula = formulaString.replaceAll("matrix_(\\w+)", "$1");

        // 2. Define a pattern to find all relevant variables in the original formula.
        //    Group 1 captures the name of the matrix variable (the part after "matrix_").
        //    Group 2 captures the other variable types.
        final Pattern pattern = Pattern.compile(
                "matrix_(\\w+)|(from_acc_car|to_acc_car|to_pc_car|from_acc_ride|to_acc_ride|to_pc_ride)"
        );

        // 3. Find all variable names and add the correct (shortened) version to the set.
        Set<String> requiredVariables = new HashSet<>();
        Matcher matcher = pattern.matcher(formulaString); // Use the original formula string for matching
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                // This was a matrix match, so add the captured short name.
                requiredVariables.add(matcher.group(1));
            } else {
                // This was another type of variable, so add the full name.
                requiredVariables.add(matcher.group(2));
            }
        }

        // 4. Build the expression using the modified formula and the collected variable names.
        ExpressionBuilder builder = new ExpressionBuilder(modifiedFormula)
                .functions(functionMin, functionMax)
                .variables(requiredVariables);

        Expression expression = builder.build();
        expression.setVariables(variableData);

        return expression.evaluate();
    }
}