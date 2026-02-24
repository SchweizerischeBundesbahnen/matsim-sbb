package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.config.variables.SBBActivities;
import org.matsim.api.core.v01.population.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SecondaryActivityTypeAssignmentModel {

    private static final Logger log = LoggerFactory.getLogger(SecondaryActivityTypeAssignmentModel.class);

    // The activity types exactly matching the 7 rightmost columns in the CSV
    private static final String[] ACTIVITY_TYPES = {
            SBBActivities.accompany, SBBActivities.business, SBBActivities.education, SBBActivities.education_continued,
            SBBActivities.leisure, SBBActivities.other, SBBActivities.shopping
    };

    // Regex to parse expressions like: current_edu==0 or age<65
    // Group 1: Attribute name, Group 2: Operator, Group 3: Value
    private static final Pattern RULE_PATTERN = Pattern.compile("\\s*([a-zA-Z_0-9]+)\\s*(==|!=|<=|>=|<|>|>==)\\s*([0-9.-]+)\\s*");

    private final List<RuleSegment> segments = new ArrayList<>();

    public SecondaryActivityTypeAssignmentModel(String csvFilePath) {
        parseCsv(csvFilePath);
    }

    /**
     * Finds the matching segment for the person and randomly selects an activity type based on the probabilities.
     */
    public String assignActivityType(Person person, Random random) {
        for (RuleSegment segment : segments) {
            if (segment.condition.test(person)) {
                return segment.chooseActivityType(random);
            }
        }
        log.warn("No activity type rule matched for person {}. Defaulting to 'other'.", person.getId());
        return "other";
    }

    private void parseCsv(String csvFilePath) {
        log.info("Loading Secondary Activity Type rules from {}", csvFilePath);

        try (BufferedReader br = new BufferedReader(new FileReader(csvFilePath))) {
            String headerLine = br.readLine();
            if (headerLine == null) return;
            String[] headers = headerLine.split(";");
            int attrExprIdx = findColumn(headers, "filter_expression");
            
            // Look up the column indices for each activity type dynamically
            int[] resultCols = new int[ACTIVITY_TYPES.length];
            for (int i = 0; i < ACTIVITY_TYPES.length; i++) {
                resultCols[i] = findColumn(headers, ACTIVITY_TYPES[i]);
            }

            String line;
            while ((line = br.readLine()) != null) {
                // Ignore empty lines
                if (line.trim().isEmpty()) continue;
                
                String[] cols = line.split(";", -1);
                if (cols.length <= attrExprIdx) continue;

                String expr = cols[attrExprIdx].trim();
                
                try {
                    // Parse probabilities
                    double[] probs = new double[ACTIVITY_TYPES.length];
                    for (int i = 0; i < ACTIVITY_TYPES.length; i++) {
                        probs[i] = Double.parseDouble(cols[resultCols[i]].trim());
                    }
                    
                    // Compile the string expression into a fast Java Predicate
                    Predicate<Person> predicate = buildPredicate(expr);
                    segments.add(new RuleSegment(expr, predicate, probs));

                } catch (NumberFormatException e) {
                    log.debug("Skipping invalid or metadata row: {}", line);
                }
            }
            log.info("Successfully loaded {} secondary activity assignment rules.", segments.size());

        } catch (IOException e) {
            throw new RuntimeException("Failed to read secondary activity rules CSV: " + csvFilePath, e);
        }
    }

    private Predicate<Person> buildPredicate(String expr) {
        if (expr == null || expr.isEmpty()) return p -> true;

        Predicate<Person> combined = p -> true;
        String[] conditions = expr.split(" & ");

        for (String cond : conditions) {
            cond = cond.trim();

            Matcher m = RULE_PATTERN.matcher(cond);
            if (m.matches()) {
                String attrName = m.group(1);
                String operator = m.group(2);
                double targetValue = Double.parseDouble(m.group(3));

                combined = combined.and(createAttributeCondition(attrName, operator, targetValue));
            } else {
                log.warn("Could not parse rule condition: '{}'. This condition will be ignored.", cond);
            }
        }
        return combined;
    }

    private Predicate<Person> createAttributeCondition(String attrName, String operator, double targetValue) {
        return person -> {
            Object val = person.getAttributes().getAttribute(attrName);
            if (val == null) return false;

            double personValue;
            // Handle various MATSim attribute types safely (Booleans mapped to 1.0/0.0)
            if (val instanceof Boolean) {
                personValue = ((Boolean) val) ? 1.0 : 0.0;
            } else if (val instanceof Number) {
                personValue = ((Number) val).doubleValue();
            } else {
                try {
                    personValue = Double.parseDouble(val.toString());
                } catch (NumberFormatException e) {
                    return false;
                }
            }

            switch (operator) {
                case "==": return personValue == targetValue;
                case "!=": return personValue != targetValue;
                case ">":  return personValue > targetValue;
                case "<":  return personValue < targetValue;
                case ">=": return personValue >= targetValue;
                case "<=": return personValue <= targetValue;
                default:   return false;
            }
        };
    }

    private int findColumn(String[] headers, String colName) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(colName)) return i;
        }
        throw new IllegalArgumentException("Column '" + colName + "' not found in CSV.");
    }

    /**
     * Internal class representing a parsed row from the CSV.
     */
    private static class RuleSegment {
        final String rawExpression;
        final Predicate<Person> condition;
        final double[] probabilities;
        final double totalProbability;

        RuleSegment(String rawExpression, Predicate<Person> condition, double[] probabilities) {
            this.rawExpression = rawExpression;
            this.condition = condition;
            this.probabilities = probabilities;
            
            double sum = 0.0;
            for (double p : probabilities) sum += p;
            this.totalProbability = sum;
        }

        String chooseActivityType(Random random) {
            // Roulette wheel selection
            if (totalProbability <= 0) return "other";

            double r = random.nextDouble() * totalProbability;
            double cumulative = 0.0;

            for (int i = 0; i < probabilities.length; i++) {
                cumulative += probabilities[i];
                if (r <= cumulative) {
                    return ACTIVITY_TYPES[i];
                }
            }
            // Fallback for extreme floating point truncation
            return ACTIVITY_TYPES[ACTIVITY_TYPES.length - 1]; 
        }
    }
}