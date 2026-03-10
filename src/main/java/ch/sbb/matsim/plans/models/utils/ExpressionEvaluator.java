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

        String modifiedFormula = formulaString;
        Set<String> shortVariableNames = new HashSet<>();

        for (String fullName : fullVariableNames) {
            String shortName;
            if (fullName.startsWith("matrix_")) {
                shortName = fullName.substring("matrix_".length());
                modifiedFormula = modifiedFormula.replace(fullName, shortName);
            } else {
                shortName = fullName;
            }
            shortVariableNames.add(shortName);
        }

        ExpressionBuilder builder = new ExpressionBuilder(modifiedFormula)
                .functions(functionMin, functionMax)
                .variables(shortVariableNames);

        return builder.build();
    }
}