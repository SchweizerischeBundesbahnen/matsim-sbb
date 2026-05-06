package ch.sbb.matsim.plans.models.utils;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.function.Function;
import net.objecthunter.exp4j.operator.Operator;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
/*
    * This class reads the impedance formulas from the CSV and compiles them into exp4j Expressions.
    * It uses a ThreadLocal cache to ensure that each thread compiles the expression only once
    *
 */
public class ExpressionEvaluator {

    private static final Operator equalOperator = new Operator("=", 2, false, 0){
        @Override public double apply(double... args) { if(args[0]==args[1]) return 1; return 0; }
    };
    private static final Function functionMin = new Function("min", 2) {
        @Override public double apply(double... args) { return Math.min(args[0], args[1]); }
    };
    private static final Function functionMax = new Function("max", 2) {
        @Override public double apply(double... args) { return Math.max(args[0], args[1]); }
    };

//    private static final Pattern VARIABLE_PATTERN = Pattern.compile(
//            "\\b(matrix_\\w+|va_impedances|from_acc_car|to_acc_car|to_pc_car|from_acc_ride|to_acc_ride|to_pc_ride|\\[([A-Za-z0-9.:-]+)\\]\\b"
//    );
    private static final Pattern VARIABLE_PATTERN = Pattern.compile(     "\\[\\s*([A-Za-z_][A-Za-z0-9_]*)\\s*\\]" +                       // group(1): any bracketed identifier (not a number)
        "|" +
        "(matrix_[A-Za-z0-9_]+|va_impedances|from_acc_car|to_acc_car|to_pc_car|from_acc_ride|to_acc_ride|to_pc_ride)" // group(2): explicit names
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
                .variables(new HashSet<String>(getShortVariableNames(formulaString)))
                .operator(equalOperator);

        return builder.build();
    }

    public List<String> getShortVariableNames(String formulaString) {
        return new ArrayList<>(getRequiredVariables(formulaString).values());
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
        if (fullVariableNames.isEmpty()) {
            fullVariableNames.add(formulaString);
        }

        Map<String, String> shortVariableNames = new HashMap<>();
        for (String fullName : fullVariableNames) {
            if (fullName.startsWith("matrix_")) {
                shortVariableNames.put(fullName, fullName.substring("matrix_".length()));
            } else if (fullName.startsWith("[")) {
                shortVariableNames.put(fullName, fullName.substring(1,fullName.length()-1));
            } else {
                shortVariableNames.put(fullName, fullName);
            }
        }

        return shortVariableNames;
    }
}