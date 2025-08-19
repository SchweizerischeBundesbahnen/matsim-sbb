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
     * @return The calculated result.
     */
    public double evaluate(String formulaString, Map<String, Double> variableData) {
        // Find all variables needed for this specific formula
        Set<String> requiredVariables = new HashSet<>();
        final Pattern pattern = Pattern.compile(
                "(matrix_\\w+|from_acc_car|to_acc_car|to_pc_car|from_acc_ride|to_acc_ride|to_pc_ride)"
        );
        Matcher matcher = pattern.matcher(formulaString);
        while (matcher.find()) {
            requiredVariables.add(matcher.group());
        }

        ExpressionBuilder builder = new ExpressionBuilder(formulaString)
                .functions(functionMin, functionMax)
                .variables(requiredVariables);

        Expression expression = builder.build();
        expression.setVariables(variableData);

        return expression.evaluate();
    }
}