package ch.sbb.matsim.plans.models.utils;

import ch.sbb.matsim.csv.CSVReader;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
}
