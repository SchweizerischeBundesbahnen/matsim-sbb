package ch.sbb.matsim.plans.models.utilitycomponents;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public class UtilityComponentFactory {

    public static UtilityComponent fromCondition(String attributeName, String operator, String valueLiteral, double beta) {
        return fromCombinedCondition(List.of(attributeName), List.of(operator), List.of(valueLiteral), beta);
    }

    public static UtilityComponent fromCombinedCondition(List<String> attributes, List<String> operators, List<String> valueLiterals, double beta) {
        if (operators.size() != valueLiterals.size()) {
            throw new IllegalArgumentException("Operators and values must match in size.");
        }

        Map<String, Predicate<Object>> conditions = new HashMap<>();
        for (int i = 0; i < attributes.size(); i++) {
            String attributeName = attributes.get(i);
            String operator = operators.get(i);
            String valueLiteral = valueLiterals.get(i);
            double compareValue = Double.parseDouble(valueLiteral);
            Predicate<Object> combinedPredicate = actual -> {
                if (actual == null) {
                    throw new IllegalArgumentException("Attribute " + attributeName + " must be a numeric value, but is null.");
                }
                if (!(actual instanceof Number)) {
                    throw new IllegalArgumentException("Attribute " + attributeName + " must be a numeric value, but is: " + actual.getClass().getSimpleName());
                }
                double v = ((Number) actual).doubleValue();
                boolean passed = switch (operator) {
                    case "<" -> v < compareValue;
                    case "<=" -> v <= compareValue;
                    case ">" -> v > compareValue;
                    case ">=" -> v >= compareValue;
                    case "==" -> v == compareValue;
                    case "!=" -> v != compareValue;
                    default -> throw new IllegalArgumentException("Unsupported operator: " + operator);
                };
                return passed;
            };

            conditions.put(attributeName, combinedPredicate);
        }

        return new PredicateComponent(conditions, beta);


    }


}

