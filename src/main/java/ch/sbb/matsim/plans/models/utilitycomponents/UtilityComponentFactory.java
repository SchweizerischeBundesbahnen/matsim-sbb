package ch.sbb.matsim.plans.models.utilitycomponents;

import java.util.List;
import java.util.function.Predicate;

public class UtilityComponentFactory {

    public static UtilityComponent fromCondition(String attributeName, String operator, String valueLiteral, double beta) {
        return fromCombinedCondition(
                attributeName,
                List.of(operator),
                List.of(valueLiteral),
                beta
        );
    }

    public static UtilityComponent fromCombinedCondition(
            String attributeName,
            List<String> operators,
            List<String> valueLiterals,
            double beta
    ) {
        if (operators.size() != valueLiterals.size()) {
            throw new IllegalArgumentException("Operators and values must match in size.");
        }

        Predicate<Object> combinedPredicate = actual -> {
            if (actual == null) {
                throw new IllegalArgumentException("Attribute " + attributeName + " must be a numeric value, but is null.");
            }
            if (!(actual instanceof Number)) {
                throw new IllegalArgumentException("Attribute " + attributeName + " must be a numeric value, but is: " + actual.getClass().getSimpleName());
            }
            double v = ((Number) actual).doubleValue();
            for (int i = 0; i < operators.size(); i++) {
                String op = operators.get(i);
                double compareValue = Double.parseDouble(valueLiterals.get(i));
                boolean passed = switch (op) {
                    case "<" -> v < compareValue;
                    case "<=" -> v <= compareValue;
                    case ">" -> v > compareValue;
                    case ">=" -> v >= compareValue;
                    case "==" -> v == compareValue;
                    case "!=" -> v != compareValue;
                    default -> throw new IllegalArgumentException("Unsupported operator: " + op);
                };
                if (!passed) return false;
            }
            return true;
        };


        return new PredicateComponent(attributeName, combinedPredicate, beta);
    }
}
