package ch.sbb.matsim.plans.models.utilitycomponents;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public class UtilityComponentFactory {

    public static UtilityComponent fromCondition(String attributeName, String operator, String valueLiteral, double beta, boolean nullable) {
        return fromCombinedCondition(List.of(attributeName), List.of(operator), List.of(valueLiteral), beta, nullable);
    }

    public static UtilityComponent fromCombinedCondition(List<String> attributes, List<String> operators, List<String> valueLiterals, double beta, boolean nullable) {
        if (operators.size() != valueLiterals.size()) {
            throw new IllegalArgumentException("Operators and values must match in size.");
        }
        if (attributes.size() == 1 && attributes.size() < operators.size()) {
            List<String> mirroredAttributes = new ArrayList<>();
            for (int i = 0; i < operators.size(); i++) {
                mirroredAttributes.add(attributes.getFirst());
            }
            attributes = mirroredAttributes;
        }
        List<Predicate<Object>> conditions = new ArrayList<>();
        for (int i = 0; i < operators.size(); i++) {
            String attributeName = attributes.get(i);
            String operator = operators.get(i);
            String valueLiteral = valueLiterals.get(i);
            List<String> finalAttributes = attributes;
            Predicate<Object> combinedPredicate = actual -> {
                if (actual == null) {
                    if (nullable) {
                        return false; // If null is allowed, treat it as not passing the condition
                    } else {
                    throw new IllegalArgumentException("Attribute " + attributeName + " must be a numeric value, but is null.");
                    }
                }
                boolean passed;
                if (actual instanceof Number value) {
                    double compareValue = Double.parseDouble(valueLiteral);
                    double v = value.doubleValue();
                    passed = switch (operator) {
                        case "<" -> v < compareValue;
                        case "<=" -> v <= compareValue;
                        case ">" -> v > compareValue;
                        case ">=" -> v >= compareValue;
                        case "==" -> v == compareValue;
                        case "!=" -> v != compareValue;
                        default -> throw new IllegalArgumentException("Unsupported operator: " + operator + " attributes: "+ finalAttributes +" operators: "+operators+" values: "+valueLiterals);
                    };
                } else if (actual instanceof String strValue) {
                    passed = switch (operator) {
                        case "==" -> valueLiteral.equalsIgnoreCase(strValue);
                        case "!=" -> !valueLiteral.equalsIgnoreCase(strValue);
                        default -> throw new IllegalArgumentException("Unsupported operator: " + operator + " attributes: "+ finalAttributes +" operators: "+operators+" values: "+valueLiterals +". String Value: "+strValue );
                    };

                } else {
                    throw new IllegalArgumentException("Attribute " + attributeName + " must be a numeric or String value, but is of type " + actual.getClass().getSimpleName() + ".");

                }
                return passed;
            };
            conditions.add(combinedPredicate);

        }

        return new PredicateComponent(attributes, conditions, beta);


    }


}

