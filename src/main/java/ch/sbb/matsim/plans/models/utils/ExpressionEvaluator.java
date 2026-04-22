package ch.sbb.matsim.plans.models.utils;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.function.Function;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/*
    * This class reads the impedance formulas from the CSV and compiles them into exp4j Expressions.
    * It uses a ThreadLocal cache to ensure that each thread compiles the expression only once
    *
 */
public class ExpressionEvaluator {

    private static final Function functionMin = new Function("min", 2) {
        @Override public double apply(double... args) { return Math.min(args[0], args[1]); }
    };
    private static final Function functionMax = new Function("max", 2) {
        @Override public double apply(double... args) { return Math.max(args[0], args[1]); }
    };

    private static final Pattern VARIABLE_PATTERN = Pattern.compile(
            "\\b(matrix_\\w+|va_impedances|from_acc_car|to_acc_car|to_pc_car|from_acc_ride|to_acc_ride|to_pc_ride)\\b"
    );

    // ThreadLocal cache to ensure thread-safety during parallelStream evaluations.
    // Maps original formulaString -> Compiled exp4j Expression
    private final ThreadLocal<Map<String, Expression>> expressionCache = ThreadLocal.withInitial(HashMap::new);

    /**
     * Evaluates a given formula string with a map of variable data.
     */
    public double evaluate(String formulaString, Map<String, Double> variableData) {
        // 1. Get the cached expression for this specific thread
        Map<String, Expression> cache = expressionCache.get();

        // 2. Fetch or compile the expression (Compute-If-Absent logic)
        Expression expression = cache.computeIfAbsent(formulaString, this::compileExpression);

        // 3. Set the variables and evaluate (Thread-safe because the Expression instance is private to this thread)
        expression.setVariables(variableData);
        return expression.evaluate();
    }

    /**
     * The heavy-lifting method. This is now only called ONCE per formula per thread!
     */
    private Expression compileExpression(String formulaString) {
        Set<String> fullVariableNames = new HashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(formulaString);
        while (matcher.find()) {
            fullVariableNames.add(matcher.group(0));
        }

        Map<String, String> shortVariableNames = getRequiredVariables(formulaString);
        String modifiedFormula = formulaString;

        for (String fullName : shortVariableNames.keySet()) {
            String shortName =  shortVariableNames.get(fullName);
            if (!shortName.equals(fullName)) {
                modifiedFormula = modifiedFormula.replace(fullName, shortName);
            }
        }

        ExpressionBuilder builder = new ExpressionBuilder(modifiedFormula)
                .functions(functionMin, functionMax)
                .variables((Set<String>) shortVariableNames.values());

        return builder.build();
    }

    /**
     * Returns the set of variable names that must be provided in the variableData map
     * to evaluate the given formulaString. Variable names are returned in the same
     * (short) form used when building the exp4j Expression (i.e. "matrix_X" becomes "X").
     */
    public Map<String, String> getRequiredVariables(String formulaString) {
        Set<String> fullVariableNames = new HashSet<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(formulaString);
        while (matcher.find()) {
            fullVariableNames.add(matcher.group(0));
        }

        Map<String, String> shortVariableNames = new HashMap<>();
        for (String fullName : fullVariableNames) {
            if (fullName.startsWith("matrix_")) {
                shortVariableNames.put(fullName, fullName.substring("matrix_".length()));
            } else {
                shortVariableNames.put(fullName, fullName);
            }
        }

        return shortVariableNames;
    }
}