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
    // Custom functions are the same...
    private static final Function functionMin = new Function("min", 2) {
        @Override public double apply(double... args) { return Math.min(args[0], args[1]); }
    };
    private static final Function functionMax = new Function("max", 2) {
        @Override public double apply(double... args) { return Math.max(args[0], args[1]); }
    };

    // This pattern finds ALL possible variable names that could appear in your formulas.
    // It's a single "OR" group, so we will use group(0) to get the full match.
    private static final Pattern VARIABLE_PATTERN = Pattern.compile(
            "\\b(matrix_\\w+|va_impedances|from_acc_car|to_acc_car|to_pc_car|from_acc_ride|to_acc_ride|to_pc_ride)\\b"
    );

    /**
     * Evaluates a given formula string with a map of variable data.
     * Assumes the formula string contains full names (e.g., "matrix_car_travel_times")
     * and the variableData map contains short names (e.g., "car_travel_times").
     *
     * @param formulaString The raw formula string from the file.
     * @param variableData  A map where keys are the SHORT variable names.
     * @return The calculated result.
     */
    public double evaluate(String formulaString, Map<String, Double> variableData) {

        // --- Step 1: Discover all variables from the original formula ---
        // We do this first to know which variables we are dealing with.
        Set<String> fullVariableNames = new HashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(formulaString);
        while (matcher.find()) {
            fullVariableNames.add(matcher.group(0)); // group(0) is the full text of the match
        }

        // --- Step 2: Create the modified formula and the set of short names for exp4j ---
        String modifiedFormula = formulaString;
        Set<String> shortVariableNames = new HashSet<>();

        for (String fullName : fullVariableNames) {
            String shortName;
            if (fullName.startsWith("matrix_")) {
                // If it's a matrix variable, strip the prefix to get the short name
                shortName = fullName.substring("matrix_".length());
                // And replace the full name with the short name in the formula string
                modifiedFormula = modifiedFormula.replace(fullName, shortName);
            } else {
                // If it's not a matrix variable, the full name is the short name
                shortName = fullName;
            }
            shortVariableNames.add(shortName);
        }

        // --- Step 3: Build and evaluate the expression ---
        // We use the MODIFIED formula and the SHORT variable names.
        ExpressionBuilder builder = new ExpressionBuilder(modifiedFormula)
                .functions(functionMin, functionMax)
                .variables(shortVariableNames);

        Expression expression = builder.build();

        // The variableData map (with short names) will now correctly match the variables
        // that exp4j expects.
        expression.setVariables(variableData);

        return expression.evaluate();
    }
}