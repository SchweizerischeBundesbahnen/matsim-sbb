package ch.sbb.matsim.plans.models.utils;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.plans.models.resolver.AttributeResolver;
import ch.sbb.matsim.plans.models.resolver.NestedAttributeResolver;
import ch.sbb.matsim.plans.models.resolver.ScaledAttributeResolver;
import ch.sbb.matsim.plans.models.utilitycomponents.BoxCoxComponent;
import ch.sbb.matsim.plans.models.utilitycomponents.ConstantComponent;
import ch.sbb.matsim.plans.models.utilitycomponents.LinearComponent;
import ch.sbb.matsim.plans.models.utilitycomponents.LogarithmicComponent;
import ch.sbb.matsim.plans.models.utilitycomponents.PiecewiseComponent;
import ch.sbb.matsim.plans.models.utilitycomponents.PredicateComponent;
import ch.sbb.matsim.plans.models.utilitycomponents.ShadowUtility;
import ch.sbb.matsim.plans.models.utilitycomponents.UtilityCalculator;
import ch.sbb.matsim.plans.models.utilitycomponents.UtilityComponent;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class ChoiceModelCSVReader {
    private ChoiceModelCSVReader() {
    }

    public static final String BETA = "beta_";
    public static final String VALUE = "value";
    private static final List<String> MODEL_REQUIRED_COLUMNS = List.of("type", "attribute", "values", "lambda");
    private static final Pattern ATTRIBUTE_SCALE_PATTERN = Pattern.compile("^(.+?)\\s*([*/])\\s*([-+]?\\d*\\.?\\d+(?:[eE][-+]?\\d+)?)$");
    private static final Pattern CONDITION_TOKEN_PATTERN = Pattern.compile("^(<=|>=|==|!=|<|>)(.+)$");

    public static Map<String, Map<String, Double>> readShadowUtilitiesFromCSV(String filePath) {
        Map<String, Map<String, Double>> shadowUtilities = new HashMap<>();
        try (CSVReader reader = new CSVReader(filePath, ";")) {
            Set<String> columns = toColumnSet(reader.getColumns());
            validateRequiredColumns(columns, List.of("aggregate_id"), filePath);
            List<String> alternatives = extractAlternatives(reader.getColumns(), filePath);
            alternatives.forEach(alternative -> shadowUtilities.put(alternative, new HashMap<>()));
            var row = reader.readLine();
            while (row != null) {
                String aggregate = row.get("aggregate_id");
                for (String alternative : alternatives) {
                    String value = row.get(BETA + alternative);
                    if (value != null && !value.isEmpty()) {
                        double beta = Double.parseDouble(value);
                        shadowUtilities.get(alternative).put(aggregate, beta);
                    }
                }
                row = reader.readLine();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return shadowUtilities;
    }


    public static Map<String, UtilityCalculator> getChoiceModelFromRows(ArrayList<Map<String, String>> rows) {
        if (rows == null || rows.isEmpty()) {
            throw new IllegalArgumentException("Choice model rows are empty.");
        }
        Map<String, UtilityCalculator> choiceModels = new HashMap<>();
        List<String> alternatives = rows.getFirst().keySet().stream().filter(s -> s.startsWith(BETA)).map(s -> s.substring(BETA.length())).toList();
        if (alternatives.isEmpty()) {
            throw new IllegalArgumentException("Missing required columns: at least one '" + BETA + "*' column is required.");
        }
        alternatives.forEach(alternative -> choiceModels.put(alternative, new UtilityCalculator()));
        for (Map<String, String> row:rows) {
            readRow(null, alternatives, choiceModels, row);
        }
        return choiceModels;
    }

    private static void readRow(String filePath, List<String> alternatives, Map<String, UtilityCalculator> choiceModels, Map<String, String> row) {
        String typeString = row.get("type").toUpperCase();
        if (typeString.startsWith("#")) {
            // Skip comment lines
            return;
        }
        boolean nullable = Boolean.parseBoolean(row.getOrDefault("nullable", "false"));
        UtilityComponent.ComponentType type = UtilityComponent.ComponentType.valueOf(typeString);
        final String attribute = row.get("attribute");
        if (type.equals(UtilityComponent.ComponentType.SHADOW_UTILS)) {
            var shadowUtilities = readShadowUtilitiesFromCSV(filePath.replace(".csv", "_shadow_utils.csv"));
            for (String alternative : alternatives) {
                choiceModels.get(alternative).addComponent(new ShadowUtility(attribute, shadowUtilities.get(alternative)));
            }
        } else {
            ParsedAttribute parsedAttribute = parseAttribute(row, type);
            AttributeResolver resolver = parsedAttribute.multiplier == 1.0
                ? new NestedAttributeResolver()
                : new ScaledAttributeResolver(new NestedAttributeResolver(), parsedAttribute.multiplier);
            for (String alternative : alternatives) {
                UtilityCalculator calculator = choiceModels.get(alternative);
                switch (type) {
                    case LINEAR ->
                        calculator.addComponent(new LinearComponent(parsedAttribute.attributePath, getBeta(alternative, row), resolver, nullable));
                    case LOGARITHMIC ->
                        calculator.addComponent(new LogarithmicComponent(parsedAttribute.attributePath, getBeta(alternative, row), resolver, nullable));
                    case CONSTANT -> calculator.addComponent(new ConstantComponent(getBeta(alternative, row)));
                    case CONDITION -> calculator.addComponent(parseConditionComponent(row, alternative,nullable));
                    case BOXCOX ->
                        calculator.addComponent(new BoxCoxComponent(parsedAttribute.attributePath, getBeta(alternative, row), Double.parseDouble(row.get("lambda")), resolver, nullable));
                    case PIECEWISE -> {
                        String valuesStrings = row.get(BETA + alternative)!=null ? row.get(BETA + alternative) : row.get(alternative);
                        double[] values = Arrays.stream(valuesStrings.split(",")).mapToDouble(Double::parseDouble).toArray();
                        calculator.addComponent(new PiecewiseComponent(attribute,
                            Arrays.stream(row.get("values").split(",")).mapToDouble(Double::parseDouble).toArray(),
                            values,nullable));
                    }
                    default -> throw new IllegalArgumentException("Unknown component type: " + type);
                }
            }
        }

    }

    private static ParsedAttribute parseAttribute(Map<String, String> row, UtilityComponent.ComponentType type) {
        String attribute = Optional.ofNullable(row.get("attribute")).orElse("").trim();
        String valuesExpression = Optional.ofNullable(row.get("values")).orElse("").trim();

        if (!(type == UtilityComponent.ComponentType.LINEAR
            || type == UtilityComponent.ComponentType.LOGARITHMIC
            || type == UtilityComponent.ComponentType.BOXCOX)) {
            return new ParsedAttribute(attribute, 1.0);
        }

        Optional<ParsedAttribute> parsedFromValues = tryParseScaledAttributeExpression(valuesExpression, attribute);
        if (parsedFromValues.isPresent()) {
            return parsedFromValues.get();
        }

        Optional<ParsedAttribute> parsedFromAttribute = tryParseScaledAttributeExpression(attribute, null);
        if (parsedFromAttribute.isPresent()) {
            return parsedFromAttribute.get();
        }

        if (!valuesExpression.isEmpty()) {
            // Allow using `values` as plain attribute override for linear-like components.
            return new ParsedAttribute(valuesExpression, 1.0);
        }
        return new ParsedAttribute(attribute, 1.0);
    }

    private static Optional<ParsedAttribute> tryParseScaledAttributeExpression(String expression, String fallbackAttribute) {
        if (expression == null || expression.isBlank()) {
            return Optional.empty();
        }

        String trimmedExpression = expression.trim();
        char firstChar = trimmedExpression.charAt(0);
        if (firstChar == '*' || firstChar == '/') {
            if (fallbackAttribute == null || fallbackAttribute.isBlank()) {
                return Optional.empty();
            }
            double factor = Double.parseDouble(trimmedExpression.substring(1).trim());
            if (factor == 0.0 && firstChar == '/') {
                throw new IllegalArgumentException("Division by zero in values expression: " + expression);
            }
            return Optional.of(new ParsedAttribute(fallbackAttribute.trim(), firstChar == '*' ? factor : 1.0 / factor));
        }

        var matcher = ATTRIBUTE_SCALE_PATTERN.matcher(trimmedExpression);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        String attributePath = matcher.group(1).trim();
        char operator = matcher.group(2).charAt(0);
        double factor = Double.parseDouble(matcher.group(3));
        if (factor == 0.0 && operator == '/') {
            throw new IllegalArgumentException("Division by zero in values expression: " + expression);
        }
        return Optional.of(new ParsedAttribute(attributePath, operator == '*' ? factor : 1.0 / factor));
    }

    private record ParsedAttribute(String attributePath, double multiplier) {
    }

    public static Map<String, UtilityCalculator> readChoiceModelFromCSV(String filePath) {
        Map<String, UtilityCalculator> choiceModels = new HashMap<>();
        try (CSVReader reader = new CSVReader(filePath, ";")) {
            Set<String> columns = toColumnSet(reader.getColumns());
            validateRequiredColumns(columns, MODEL_REQUIRED_COLUMNS, filePath);
            List<String> alternatives = extractAlternatives(reader.getColumns(), filePath);
            alternatives.forEach(alternative -> choiceModels.put(alternative, new UtilityCalculator()));
            var row = reader.readLine();
            while (row != null) {
                readRow(filePath, alternatives, choiceModels, row);
                row = reader.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return choiceModels;
    }

    public static UtilityCalculator readRegressionModelFromCSV(String filePath) {
        Map<String, UtilityCalculator> choiceModels = new HashMap<>();
        try (CSVReader reader = new CSVReader(filePath, ";")) {
            Set<String> columns = toColumnSet(reader.getColumns());
            validateRequiredColumns(columns, MODEL_REQUIRED_COLUMNS, filePath);
            if (!columns.contains(VALUE) && !columns.contains(BETA + VALUE)) {
                throw new IllegalArgumentException("Missing required columns in '" + filePath + "': expected '" + VALUE + "' or '" + BETA + VALUE + "'.");
            }
            List<String> alternatives = List.of(VALUE);
            alternatives.forEach(alternative -> choiceModels.put(alternative, new UtilityCalculator()));
            var row = reader.readLine();
            while (row != null) {
                readRow(filePath, alternatives, choiceModels, row);
                row = reader.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return choiceModels.get(VALUE);
    }

    private static double getBeta(String alternative, Map<String, String> row) {
        String betaKey = BETA + alternative;
        String betaValue = row.get(betaKey);
        if (betaValue == null) {
            betaValue = row.get(alternative);
        }
        try {
            return Double.parseDouble(betaValue);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static Set<String> toColumnSet(String[] columns) {
        return Arrays.stream(columns)
            .map(String::trim)
            .collect(Collectors.toSet());
    }

    private static void validateRequiredColumns(Set<String> columns, Collection<String> requiredColumns, String filePath) {
        List<String> missingColumns = requiredColumns.stream()
            .filter(required -> !columns.contains(required))
            .toList();
        if (!missingColumns.isEmpty()) {
            throw new IllegalArgumentException("Missing required columns in '" + filePath + "': " + String.join(", ", missingColumns));
        }
    }

    private static List<String> extractAlternatives(String[] columns, String filePath) {
        List<String> alternatives = Arrays.stream(columns)
            .filter(s -> s.startsWith(BETA))
            .map(s -> s.substring(BETA.length()))
            .toList();
        if (alternatives.isEmpty()) {
            throw new IllegalArgumentException("Missing required columns in '" + filePath + "': at least one '" + BETA + "*' column is required.");
        }
        return alternatives;
    }

    private static UtilityComponent parseConditionComponent(Map<String, String> row, String alternative,boolean nullable) {
        String attribute = row.get("attribute").trim();
        List<String> attributes = new ArrayList<>();
        for (String part : attribute.split(",")) {
            attributes.add(part.trim());
        }
        String values = row.get("values").trim(); // e.g. "<90,>0"
        double beta = getBeta(alternative, row);

        List<String> valueParts = Arrays.stream(values.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toList();
        while (attributes.size() < valueParts.size()) {
            attributes.add(attribute);
            // Ensure that the number of attributes matches the number of value parts by repeating the attribute.
        }

        List<Predicate<Object>> conditions = new ArrayList<>();
        for (String valuePart : valueParts) {
            String[] disjunctionParts = Arrays.stream(valuePart.split("\\|"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
            List<Predicate<Object>> disjunctionConditions = new ArrayList<>();
            for (String token : disjunctionParts) {
                ParsedConditionToken parsedToken = parseConditionToken(token, valuePart);
                disjunctionConditions.add(buildAtomicCondition(parsedToken.operator(), parsedToken.valueLiteral(), nullable));
            }
            conditions.add(actual -> disjunctionConditions.stream().anyMatch(predicate -> predicate.test(actual)));
        }

        return new PredicateComponent(attributes, conditions, beta);
    }

    private static ParsedConditionToken parseConditionToken(String token, String sourceExpression) {
        var matcher = CONDITION_TOKEN_PATTERN.matcher(token);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid condition token '" + token + "' in values expression '" + sourceExpression + "'.");
        }
        return new ParsedConditionToken(matcher.group(1), matcher.group(2).trim());
    }

    private static Predicate<Object> buildAtomicCondition(String operator, String valueLiteral, boolean nullable) {
        return actual -> evaluateAtomicCondition(actual, operator, valueLiteral, nullable);
    }

    private static boolean evaluateAtomicCondition(Object actual, String operator, String valueLiteral, boolean nullable) {
        if (actual == null) {
            if (nullable) {
                return false;
            }
            throw new IllegalArgumentException("Attribute must be a numeric or String value, but is null.");
        }
        if (actual instanceof Number numberValue) {
            return compareNumeric(numberValue.doubleValue(), operator, valueLiteral);
        }
        if (actual instanceof String stringValue) {
            return compareString(stringValue, operator, valueLiteral);
        }
        throw new IllegalArgumentException("Attribute must be a numeric or String value, but is of type "
            + actual.getClass().getSimpleName() + ".");
    }

    private static boolean compareNumeric(double actualValue, String operator, String valueLiteral) {
        double compareValue = Double.parseDouble(valueLiteral);
        return switch (operator) {
            case "<" -> actualValue < compareValue;
            case "<=" -> actualValue <= compareValue;
            case ">" -> actualValue > compareValue;
            case ">=" -> actualValue >= compareValue;
            case "==" -> actualValue == compareValue;
            case "!=" -> actualValue != compareValue;
            default -> throw new IllegalArgumentException("Unsupported operator for numeric value: " + operator);
        };
    }

    private static boolean compareString(String actualValue, String operator, String valueLiteral) {
        return switch (operator) {
            case "==" -> valueLiteral.equalsIgnoreCase(actualValue);
            case "!=" -> !valueLiteral.equalsIgnoreCase(actualValue);
            default -> throw new IllegalArgumentException("Unsupported operator for String value: " + operator);
        };
    }

    private record ParsedConditionToken(String operator, String valueLiteral) {
    }


}