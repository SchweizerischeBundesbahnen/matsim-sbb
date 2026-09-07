package ch.sbb.matsim.plans.models.resolver;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.matsim.utils.objectattributes.attributable.Attributable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves simple nested attributes as-is and evaluates arithmetic expressions
 * (e.g. household.nb_of_kids+household.nb_of_adults) to a numeric value.
 */
public class ExpressionAttributeResolver implements AttributeResolver {

    private static final Pattern ATTRIBUTE_TOKEN_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_.]*");
    private final AttributeResolver delegate;

    public ExpressionAttributeResolver(AttributeResolver delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object resolve(Attributable root, String attributePath) {
        if (!containsArithmeticOperator(attributePath)) {
            return delegate.resolve(root, attributePath);
        }

        ParsedExpression parsedExpression = parse(attributePath);
        if (parsedExpression.attributeToVariable().isEmpty()) {
            return delegate.resolve(root, attributePath);
        }

        Expression expression = new ExpressionBuilder(parsedExpression.expression())
                .variables(new HashSet<>(parsedExpression.attributeToVariable().values()))
                .build();

        for (Map.Entry<String, String> mapping : parsedExpression.attributeToVariable().entrySet()) {
            Object value = delegate.resolve(root, mapping.getKey());
            if (value == null) {
                return null;
            }
            double numericValue;
            if (value instanceof Number number) {
                numericValue = number.doubleValue();
            } else {
                try {
                    numericValue = Double.parseDouble(value.toString());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Expression attribute '" + mapping.getKey() + "' is not numeric: " + value, e);
                }
            }
            expression.setVariable(mapping.getValue(), numericValue);
        }

        return expression.evaluate();
    }

    private static boolean containsArithmeticOperator(String attributePath) {
        return attributePath != null
                && (attributePath.indexOf('+') >= 0
                || attributePath.indexOf('-') >= 0
                || attributePath.indexOf('*') >= 0
                || attributePath.indexOf('/') >= 0
                || attributePath.indexOf('(') >= 0
                || attributePath.indexOf(')') >= 0);
    }

    private static ParsedExpression parse(String expression) {
        Matcher matcher = ATTRIBUTE_TOKEN_PATTERN.matcher(expression);
        Map<String, String> attributeToVariable = new LinkedHashMap<>();
        StringBuilder sanitized = new StringBuilder();
        int cursor = 0;

        while (matcher.find()) {
            String token = matcher.group();
            sanitized.append(expression, cursor, matcher.start());

            String variableName;
            if (isNumericToken(token)) {
                variableName = token;
            } else {
                variableName = attributeToVariable.computeIfAbsent(token, _ -> "v" + attributeToVariable.size());
            }
            sanitized.append(variableName);
            cursor = matcher.end();
        }
        sanitized.append(expression.substring(cursor));

        return new ParsedExpression(sanitized.toString(), attributeToVariable);
    }

    private static boolean isNumericToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isDigit(token.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private record ParsedExpression(String expression, Map<String, String> attributeToVariable) {
    }
}


