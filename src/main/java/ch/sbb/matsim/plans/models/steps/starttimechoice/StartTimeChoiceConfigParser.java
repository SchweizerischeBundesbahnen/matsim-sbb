package ch.sbb.matsim.plans.models.steps.starttimechoice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.matsim.api.core.v01.population.Person;


public class StartTimeChoiceConfigParser {

    private static final Logger log = LoggerFactory.getLogger(StartTimeChoiceConfigParser.class);

    // Regex to identify time bin headers like "3600_7199"
    private static final Pattern TIME_BIN_PATTERN = Pattern.compile("^(\\d+)_(\\d+)$");

    public static Map<String, List<StartTimeChoiceModel.TimeSeriesRule>> parse(String csvFilePath) {
        log.info("Loading Start Time Choice configurations from {}", csvFilePath);
        Map<String, List<StartTimeChoiceModel.TimeSeriesRule>> rulesByActivity = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(csvFilePath))) {
            String headerLine = br.readLine();
            if (headerLine == null) throw new IllegalArgumentException("CSV file is empty.");

            String[] headers = headerLine.split(";");

            // 1. Identify static columns
            int activityIdx = findColumn(headers, "activity");
            int exprIdx = findColumn(headers, "attribute_expression");
            int expBetaIdx = findColumn(headers, "exp_beta");

            // 2. Identify dynamic time bin columns
            List<TimeBinColumn> timeBinColumns = new ArrayList<>();
            for (int i = 0; i < headers.length; i++) {
                Matcher matcher = TIME_BIN_PATTERN.matcher(headers[i].trim());
                if (matcher.matches()) {
                    int startSec = Integer.parseInt(matcher.group(1));
                    int endSec = Integer.parseInt(matcher.group(2));
                    timeBinColumns.add(new TimeBinColumn(i, startSec, endSec));
                }
            }

            if (timeBinColumns.isEmpty()) {
                log.warn("No time bin columns (e.g., '0_3599') found in the CSV header!");
            }

            // 3. Parse the rows
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] cols = line.split(";", -1);

                try {
                    String activityType = cols[activityIdx].trim();
                    String expression = cols[exprIdx].trim();
                    double expBeta = Double.parseDouble(cols[expBetaIdx].trim());

                    // Compile the logical expression into our fast BiPredicate
                    BiPredicate<Person, Double> condition = TimeSeriesPredicateParser.parse(expression);

                    // Extract the probabilities for all time bins
                    List<StartTimeChoiceModel.TimeBin> timeBins = new ArrayList<>();
                    double sumProb = 0.0;
                    for (TimeBinColumn tbc : timeBinColumns) {
                        double prob = 0.0;
                        if (cols.length > tbc.colIndex && !cols[tbc.colIndex].trim().isEmpty()) {
                            prob = Double.parseDouble(cols[tbc.colIndex].trim());
                        }
                        timeBins.add(new StartTimeChoiceModel.TimeBin(tbc.startSec, tbc.endSec, prob));
                        sumProb += prob;
                    }

                    // Optional: Warn if the probabilities for a series don't sum close to 1.0 (or 100%)
                    if (Math.abs(sumProb - 1.0) > 0.05 && sumProb > 0) {
                        log.debug("Time series {} probabilities sum to {} (expected ~1.0)", activityType, sumProb);
                    }

                    // Create rule and add it to the map
                    StartTimeChoiceModel.TimeSeriesRule rule = new StartTimeChoiceModel.TimeSeriesRule(
                            activityType, expression, condition, expBeta, timeBins
                    );

                    rulesByActivity.computeIfAbsent(activityType, k -> new ArrayList<>()).add(rule);

                } catch (Exception e) {
                    log.debug("Skipping invalid or metadata row: {} | Reason: {}", line, e.getMessage());
                }
            }

            log.info("Successfully loaded Start Time rules for {} distinct activities.", rulesByActivity.size());

        } catch (IOException e) {
            throw new RuntimeException("Failed to read Start Time Config CSV: " + csvFilePath, e);
        }

        return rulesByActivity;
    }

    private static int findColumn(String[] headers, String colName) {
        for (int i = 0; i < headers.length; i++) {
            if (headers[i].trim().equalsIgnoreCase(colName)) return i;
        }
        throw new IllegalArgumentException("Column '" + colName + "' not found in CSV.");
    }

    // Helper class to map CSV column indices to time intervals
    private record TimeBinColumn(int colIndex, int startSec, int endSec) {}
}