    package ch.sbb.matsim.plans.models.utils;

    import ch.sbb.matsim.csv.CSVReader;
    import ch.sbb.matsim.plans.models.utilitycomponents.*;

    import java.io.IOException;
    import java.util.*;

    public class ChoiceModelCSVReader {


        public static final String BETA = "beta_";
        public static final String VALUE = "value";

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


        public static Map<String, UtilityCalculator> getChoiceModelFromRows(ArrayList<Map<String, String>> rows) {
            Map<String, UtilityCalculator> choiceModels = new HashMap<>();
            List<String> alternatives = rows.getFirst().keySet().stream().filter(s -> s.startsWith(BETA)).map(s -> s.substring(BETA.length())).toList();
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
                for (String alternative : alternatives) {
                    UtilityCalculator calculator = choiceModels.get(alternative);
                    switch (type) {
                        case LINEAR ->
                                calculator.addComponent(new LinearComponent(attribute, getBeta(alternative, row), nullable));
                        case LOGARITHMIC ->
                                calculator.addComponent(new LogarithmicComponent(attribute, getBeta(alternative, row), nullable));
                        case CONSTANT -> calculator.addComponent(new ConstantComponent(getBeta(alternative, row)));
                        case CONDITION -> calculator.addComponent(parseConditionComponent(row, alternative,nullable));
                        case BOXCOX ->
                                calculator.addComponent(new BoxCoxComponent(attribute, getBeta(alternative, row), Double.parseDouble(row.get("lambda")), nullable));
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
        public static Map<String, UtilityCalculator> readChoiceModelFromCSV(String filePath) {
            Map<String, UtilityCalculator> choiceModels = new HashMap<>();
            try (CSVReader reader = new CSVReader(filePath, ";")) {
                List<String> alternatives = Arrays.stream(reader.getColumns()).filter(s -> s.startsWith(BETA)).map(s -> s.substring(BETA.length())).toList();
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

        private static UtilityComponent parseConditionComponent(Map<String, String> row, String alternative,boolean nullable) {
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

            return UtilityComponentFactory.fromCombinedCondition(attributes, operators, valueLiterals, beta,nullable);
        }


    }