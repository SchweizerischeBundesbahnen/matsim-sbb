package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.plans.matrices.Matrices;
import ch.sbb.matsim.plans.matrices.MatrixNames;
import ch.sbb.matsim.zones.Zone;
import org.matsim.api.core.v01.Id;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SecondaryKalifMatrixCache {

    private static final Logger log = LoggerFactory.getLogger(SecondaryKalifMatrixCache.class);

    // Maps ActivityType -> double[originIdx][destIdx]
    private final Map<String, float[][]> kalifMatricesByActivity = new HashMap<>();

    public SecondaryKalifMatrixCache(String csvFilePath, Matrices matrices, List<Zone> zoneList, Map<Id<Zone>, Integer> zoneIndexMap) {
        parseAndBuildMatrices(csvFilePath, matrices, zoneList, zoneIndexMap);
    }

    public float[][] getKalifMatrixForActivity(String activityType) {
        return kalifMatricesByActivity.get(activityType);
    }

    private void parseAndBuildMatrices(String csvFilePath, Matrices matrices, List<Zone> zoneList, Map<Id<Zone>, Integer> zoneIndexMap) {
        log.info("Loading and pre-calculating Secondary Kalif Matrices from {}", csvFilePath);
        int numZones = zoneList.size();

        try (BufferedReader br = new BufferedReader(new FileReader(csvFilePath))) {
            String headerLine = br.readLine();
            if (headerLine == null) return;
            
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] cols = line.split(";", -1);
                if (cols.length < 2) continue;

                String activityType = cols[0].trim();
                String expression = cols[1].trim();
                
                // Initialize the combined flat array for this activity
                float[][] combinedKalif = new float[numZones][numZones];
                
                // Parse the expression (e.g., "kalif_secondary+0.4*kalif_work")
                // 1. Split by '+'
                String[] terms = expression.split("\\+");
                for (String term : terms) {
                    term = term.trim();
                    if (term.isEmpty()) continue;

                    double factor = 1.0;
                    String matrixName = term;

                    // 2. Split by '*' if a factor is present
                    if (term.contains("*")) {
                        String[] parts = term.split("\\*");
                        factor = Double.parseDouble(parts[0].trim());
                        matrixName = parts[1].trim();
                    }

                    // 3. Add the scaled base matrix to our combined array
                    var baseMatrix = matrices.getKalifMatrix(MatrixNames.KalifMatrices.valueOf(matrixName.toUpperCase()));
                    if (baseMatrix == null) {
                        log.warn("Kalif matrix '{}' not found in Matrices object! Used in expression for '{}'.", matrixName, activityType);
                        continue;
                    }

                    for (int o = 0; o < numZones; o++) {
                        Id<Zone> originId = zoneList.get(o).getId();
                        for (int d = 0; d < numZones; d++) {
                            Id<Zone> destId = zoneList.get(d).getId();
                            // Add factor * base value
                            combinedKalif[o][d] += (float) factor * baseMatrix.get(originId, destId);
                        }
                    }
                }
                
                kalifMatricesByActivity.put(activityType, combinedKalif);
                log.debug("Built Kalif matrix for activity '{}' using expression '{}'", activityType, expression);
            }
            log.info("Successfully built Kalif matrices for {} secondary activities.", kalifMatricesByActivity.size());

        } catch (IOException e) {
            throw new RuntimeException("Failed to read Secondary Kalif CSV: " + csvFilePath, e);
        }
    }
}