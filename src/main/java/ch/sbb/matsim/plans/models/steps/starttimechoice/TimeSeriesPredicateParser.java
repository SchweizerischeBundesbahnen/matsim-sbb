package ch.sbb.matsim.plans.models.steps.starttimechoice;

import org.matsim.api.core.v01.population.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses complex logical strings like: trip.distance<4 & (age<65 | (age>=65 & level_of_employment>=20))
 * into a fast, native Java BiPredicate<Person, Double>.
 */
public class TimeSeriesPredicateParser {

    private static final Logger log = LoggerFactory.getLogger(TimeSeriesPredicateParser.class);

    // Tokenizes the string into brackets, logic operators, and raw conditions
    private static final Pattern TOKENIZER = Pattern.compile("([&|()])|([^&|()]+)");
    
    // Parses a specific condition like "trip.distance < 4" or "age >= 65"
    private static final Pattern CONDITION_PATTERN = Pattern.compile("\\s*([a-zA-Z_0-9.]+)\\s*(==|!=|<=|>=|<|>|=)\\s*([0-9.-]+)\\s*");

    public static BiPredicate<Person, Double> parse(String expression) {
        if (expression == null || expression.trim().isEmpty()) {
            return (person, tripDistance) -> true; // Empty expression always matches
        }

        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKENIZER.matcher(expression);
        while (matcher.find()) {
            String token = matcher.group().trim();
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }

        Parser parser = new Parser(tokens);
        try {
            return parser.parseExpression();
        } catch (Exception e) {
            log.error("Failed to parse expression: '{}'. Returning false.", expression, e);
            return (person, tripDistance) -> false;
        }
    }

    /**
     * Inner class that handles the Recursive Descent parsing.
     * Evaluates in order: Brackets () -> AND (&) -> OR (|)
     */
    private static class Parser {
        private final List<String> tokens;
        private int pos = 0;

        Parser(List<String> tokens) {
            this.tokens = tokens;
        }

        // Parses OR conditions (|)
        BiPredicate<Person, Double> parseExpression() {
            BiPredicate<Person, Double> left = parseTerm();
            while (pos < tokens.size() && tokens.get(pos).equals("|")) {
                pos++; // consume '|'
                BiPredicate<Person, Double> right = parseTerm();
                left = left.or(right);
            }
            return left;
        }

        // Parses AND conditions (&)
        BiPredicate<Person, Double> parseTerm() {
            BiPredicate<Person, Double> left = parseFactor();
            while (pos < tokens.size() && tokens.get(pos).equals("&")) {
                pos++; // consume '&'
                BiPredicate<Person, Double> right = parseFactor();
                left = left.and(right);
            }
            return left;
        }

        // Parses Brackets or single conditions
        BiPredicate<Person, Double> parseFactor() {
            String token = tokens.get(pos);
            if (token.equals("(")) {
                pos++; // consume '('
                BiPredicate<Person, Double> expr = parseExpression();
                if (pos < tokens.size() && tokens.get(pos).equals(")")) {
                    pos++; // consume ')'
                } else {
                    throw new IllegalArgumentException("Missing closing parenthesis!");
                }
                return expr;
            } else {
                pos++; // consume the condition string
                return buildCondition(token);
            }
        }

        // Converts "age < 65" into a functional evaluation
        private BiPredicate<Person, Double> buildCondition(String conditionStr) {
            Matcher m = CONDITION_PATTERN.matcher(conditionStr);
            if (!m.matches()) {
                throw new IllegalArgumentException("Invalid condition syntax: " + conditionStr);
            }

            String attrName = m.group(1);
            String operator = m.group(2).equals("=") ? "==" : m.group(2); // treat '=' as '=='
            double targetValue = Double.parseDouble(m.group(3));

            return (person, tripDistance) -> {
                double actualValue;

                // 1. Handle the special case for Tour/Trip variables!
                if (attrName.equalsIgnoreCase("trip.distance")) {
                    actualValue = tripDistance;
                } 
                // 2. Handle standard Person attributes
                else {
                    Object val = person.getAttributes().getAttribute(attrName);
                    if (val == null) return false;

                    if (val instanceof Boolean) {
                        actualValue = ((Boolean) val) ? 1.0 : 0.0;
                    } else if (val instanceof Number) {
                        actualValue = ((Number) val).doubleValue();
                    } else {
                        try {
                            actualValue = Double.parseDouble(val.toString());
                        } catch (NumberFormatException e) {
                            return false;
                        }
                    }
                }

                // 3. Evaluate the logic
                switch (operator) {
                    case "==": return actualValue == targetValue;
                    case "!=": return actualValue != targetValue;
                    case ">":  return actualValue > targetValue;
                    case "<":  return actualValue < targetValue;
                    case ">=": return actualValue >= targetValue;
                    case "<=": return actualValue <= targetValue;
                    default:   return false;
                }
            };
        }
    }
}