package ch.sbb.matsim.plans.models.utils;

import ch.sbb.matsim.csv.CSVReader;
import ch.sbb.matsim.plans.models.utilitycomponents.*;

import java.io.IOException;
import java.util.*;

public class ChoiceModelCSVReader {


    public static final String BETA = "beta_";

    public static Map<String, Map<String, Double>> readShadowUtilitiesFromCSV(String filePath) {
        Map<String, Map<String, Double>> shadowUtilities = new HashMap<>();
        try (CSVReader reader = new CSVReader(filePath, ";")) {
            List<String> alternatives = Arrays.stream(reader.getColumns()).filter(s -> s.startsWith(BETA)).map(s -> s.substring(BETA.length())).toList();
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


    public static Map<String, UtilityCalculator> readChoiceModelFromCSV(String filePath) {
        Map<String, UtilityCalculator> choiceModels = new HashMap<>();
        try (CSVReader reader = new CSVReader(filePath, ";")) {
            List<String> alternatives = Arrays.stream(reader.getColumns()).filter(s -> s.startsWith(BETA)).map(s -> s.substring(BETA.length())).toList();
            alternatives.forEach(alternative -> choiceModels.put(alternative, new UtilityCalculator()));
            var row = reader.readLine();
            while (row != null) {
                String typeString = row.get("type").toUpperCase();
                if (typeString.startsWith("#")) {
                    // Skip comment lines
                    row = reader.readLine();
                    continue;
                }
                UtilityComponent.ComponentType type = UtilityComponent.ComponentType.valueOf(typeString);
                final String attribute = row.get("attribute");
                if (type.equals(UtilityComponent.ComponentType.SHADOW_UTILS)) {
                    var shadowUtilities = readShadowUtilitiesFromCSV(filePath.replace(".csv", "_shadow_utils.csv"));
                    for (String alternative : alternatives) {
                        choiceModels.get(alternative).addComponent(new ShadowUtility(attribute, shadowUtilities.get(alternative)));
                    }
                } else {
                    for (String alternative : alternatives) {
                        UtilityCalculator calculator = choiceModels.get(alternative);
                        switch (type) {
                            case LINEAR ->
                                    calculator.addComponent(new LinearComponent(attribute, getBeta(alternative, row)));
                            case LOGARITHMIC ->
                                    calculator.addComponent(new LogarithmicComponent(attribute, getBeta(alternative, row)));
                            case CONSTANT -> calculator.addComponent(new ConstantComponent(getBeta(alternative, row)));
                            case CONDITION -> calculator.addComponent(parseConditionComponent(row, alternative));
                            case BOXCOX ->
                                    calculator.addComponent(new BoxCoxComponent(attribute, getBeta(alternative, row), Double.parseDouble(row.get("lambda"))));
                            case PIECEWISE -> calculator.addComponent(new PiecewiseComponent(attribute,
                                    Arrays.stream(row.get("values").split(",")).mapToDouble(Double::parseDouble).toArray(),
                                    Arrays.stream(row.get(BETA + alternative).split(",")).mapToDouble(Double::parseDouble).toArray()));
                            default -> throw new IllegalArgumentException("Unknown component type: " + type);


                        }
                    }
                }
                row = reader.readLine();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return choiceModels;
    }

    private static double getBeta(String alternative, Map<String, String> row) {
        String betaKey = BETA + alternative;
        String betaValue = row.get(betaKey);
        return Double.parseDouble(betaValue);
    }

    private static UtilityComponent parseConditionComponent(Map<String, String> row, String alternative) {
        String attribute = row.get("attribute").trim();
        List<String> attributes = new ArrayList<>();
        for (String part : attribute.split(",")) {
            attributes.add(part.trim());
        }
        String values = row.get("values").trim(); // e.g. "<90,>0"
        double beta = getBeta(alternative, row);

        List<String> operators = new ArrayList<>();
        List<String> valueLiterals = new ArrayList<>();

        for (String part : values.split(",")) {
            part = part.trim();
            // Extract operator and value literal
            String op = part.replaceAll("\\p{L}|\\d", "");
            String value = part.replaceAll("[<>=!]+", "");
            operators.add(op);
            valueLiterals.add(value);
        }
        while (attributes.size() < operators.size()) {
            attributes.add(attribute);
            // Ensure that the number of attributes matches the number of operators and values by repeating the attribute
        }

        return UtilityComponentFactory.fromCombinedCondition(attributes, operators, valueLiterals, beta);
    }


}
