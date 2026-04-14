package ch.sbb.matsim.plans.models.steps;

import org.apache.commons.math3.distribution.WeibullDistribution;
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

public class ActivityDurationAssignmentModel {

    private static final Logger log = LoggerFactory.getLogger(ActivityDurationAssignmentModel.class);

    // Regex to parse expressions like: current_edu=0 or age<65
    // Note: Since this CSV seems to use single `=` instead of `==`, the regex handles both.
    private static final Pattern RULE_PATTERN = Pattern.compile("\\s*([a-zA-Z_0-9]+)\\s*(==|!=|<=|>=|<|>|=)\\s*([0-9.-]+)\\s*");

    private final List<DurationRule> rules = new ArrayList<>();

    public ActivityDurationAssignmentModel(String csvFilePath) {
        parseCsv(csvFilePath);
    }

    /**
     * Determines the correct rule for the given person and activity type,
     * and draws a random duration bounded by MIN and MAX.
     *
     * @return Activity duration in SECONDS.
     */
    public double getDurationInSeconds(Person person, String activityType, Random random) {
        for (DurationRule rule : rules) {
            // Must match the activity type AND the person attributes
            if (rule.activityType.equals(activityType) && rule.condition.test(person)) {
                double durationInHours = rule.drawDurationInHours(random);
                return durationInHours * 3600.0;
            }
        }
        
        log.warn("No duration rule matched for person {} and activity '{}'. Defaulting to 1 hour.", person.getId(), activityType);
        return 3600.0; 
    }

    private void parseCsv(String csvFilePath) {
        log.info("Loading Activity Duration rules from {}", csvFilePath);

        try (BufferedReader br = new BufferedReader(new FileReader(csvFilePath))) {
            String headerLine = br.readLine();
            if (headerLine == null) return;
            String[] headers = headerLine.split(";");

            int actTypeIdx = findColumn(headers, "activityType");
            int attrExprIdx = findColumn(headers, "ATTREXPR");
            int param1Idx = findColumn(headers, "PARAM_1"); // Changed
            int distIdx = findColumn(headers, "DISTRIBUTION");
            int maxIdx = findColumn(headers, "MAX");
            int minIdx = findColumn(headers, "MIN");
            int param2Idx = findColumn(headers, "PARAM_2"); // Changed

            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] cols = line.split(";", -1);

                // Safety check: ensure the row has enough columns
                if (cols.length <= Math.max(param1Idx, param2Idx)) continue;

                try {
                    String activityType = cols[actTypeIdx].trim();
                    String expr = cols[attrExprIdx].trim();
                    double param1 = Double.parseDouble(cols[param1Idx].trim());
                    String distName = cols[distIdx].trim().toLowerCase();
                    double max = Double.parseDouble(cols[maxIdx].trim());
                    double min = Double.parseDouble(cols[minIdx].trim());
                    double param2 = Double.parseDouble(cols[param2Idx].trim());

                    Predicate<Person> predicate = buildPredicate(expr);
                    rules.add(new DurationRule(activityType, expr, predicate, param1, param2, min, max, distName));

                } catch (NumberFormatException e) {
                    log.debug("Skipping invalid or metadata row: {}", line);
                }
            }
            log.info("Successfully loaded {} activity duration rules.", rules.size());

        } catch (IOException e) {
            throw new RuntimeException("Failed to read activity duration CSV: " + csvFilePath, e);
        }
    }

    private Predicate<Person> buildPredicate(String expr) {
        if (expr == null || expr.isEmpty()) return _ -> true;

        Predicate<Person> combined = _ -> true;
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
        // Treat single '=' from CSV as '=='
        final String cleanOperator = operator.equals("=") ? "==" : operator;

        return person -> {
            Object val = person.getAttributes().getAttribute(attrName);

            double personValue;
            switch (val) {
                case null -> personValue = 0.0; // Default to 0 if attribute is missing
                case Boolean b -> personValue = b ? 1.0 : 0.0;
                case Number number -> personValue = number.doubleValue();
                default -> {
                    try {
                        personValue = Double.parseDouble(val.toString());
                    } catch (NumberFormatException e) {
                        return false;
                    }
                }
            }

            return switch (cleanOperator) {
                case "==" -> personValue == targetValue;
                case "!=" -> personValue != targetValue;
                case ">" -> personValue > targetValue;
                case "<" -> personValue < targetValue;
                case ">=" -> personValue >= targetValue;
                case "<=" -> personValue <= targetValue;
                default -> false;
            };
        };
    }

    private int findColumn(String[] headers, String colName) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(colName)) return i;
        }
        throw new IllegalArgumentException("Column '" + colName + "' not found in CSV.");
    }

    /**
         * Internal class representing a parsed duration rule.
         */
        private record DurationRule(String activityType, String rawExpression, Predicate<Person> condition, double param1,
                                    double param2, double min, double max, String distributionType) {

        double drawDurationInHours(Random random) {
                double rawDuration;

                switch (distributionType) {
                    case "normal":
                        // param1 = Mean, param2 = StdDev
                        rawDuration = param1 + random.nextGaussian() * param2;
                        break;
                    case "lognormal":
                        // param1 = Mu (Location), param2 = Sigma (Scale)
                        double gaussianDist = param1 + random.nextGaussian() * param2;
                        rawDuration = Math.exp(gaussianDist);
                        break;
                    case "weibull":
                        // param1 = Shape (k), param2 = Scale (lambda)
                        WeibullDistribution weibull = new WeibullDistribution(param1, param2);
                        weibull.reseedRandomGenerator(random.nextLong());
                        rawDuration = weibull.sample();
                        break;
                    default:
                        rawDuration = param1; // Fallback to PARAM_1 if unknown
                }

                // Clamp between MIN and MAX
                return Math.max(min, Math.min(max, rawDuration));
            }
        }
}