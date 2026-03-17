package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.plans.matrices.Matrices;
import ch.sbb.matsim.plans.models.formulas.ModeImpedanceFormulaRepository;
import ch.sbb.matsim.plans.models.utils.Calculations;
import ch.sbb.matsim.plans.models.utils.ExpressionEvaluator;
import ch.sbb.matsim.plans.synpop.DecisionStrata;
import ch.sbb.matsim.zones.Zone;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SecondaryDestinationLogsumCache {
    private static final Logger log = LogManager.getLogger(SecondaryDestinationLogsumCache.class);

    private final Matrices matrices;
    private final ModeImpedanceFormulaRepository formulaRepository;
    private final ExpressionEvaluator expressionEvaluator;
    private final List<Zone> zoneList;


    private final Map<String, float[][]> cache = new ConcurrentHashMap<>();

    public SecondaryDestinationLogsumCache(Matrices matrices, 
                                           ModeImpedanceFormulaRepository formulaRepository, 
                                           ExpressionEvaluator expressionEvaluator,
                                           List<Zone> zoneList) {
        this.matrices = matrices;
        this.formulaRepository = formulaRepository;
        this.expressionEvaluator = expressionEvaluator;
        this.zoneList = zoneList;
    }

    /**
     * Lazily gets or computes the logsum matrix for a specific activity and stratum.
     * Caches the result so subsequent global loops don't have to recompute it.
     */
    public float[][] getOrComputeLogsumMatrix(String activityType, DecisionStrata strata) {
        String key = activityType + "_" + strata.getStrataName();

        // ConcurrentHashMap safely handles threads asking for the same matrix simultaneously
        return cache.computeIfAbsent(key, k -> {
            log.info("Computing Secondary Logsum matrix for {}...", key);
            float[][] matrix = computeMatrix(activityType, strata);
            log.info("Completed computing Secondary Logsum matrix for {}.", key);
            return matrix;
        });
    }

    private float[][] computeMatrix(String activityType, DecisionStrata strata) {
        int numZones = zoneList.size();
        float[][] matrix = new float[numZones][numZones];

        Map<String, String> modeFormulas = formulaRepository.getModeFormulas(activityType, strata);
        if (modeFormulas == null || modeFormulas.isEmpty()) {
            log.warn("No mode formulas found for {}. Matrix will be filled with MIN_LOGSUM.", activityType);
            for (int i = 0; i < numZones; i++) {
                for (int j = 0; j < numZones; j++) {
                    matrix[i][j] = -20.0f;
                }
            }
            return matrix;
        }

        // Parallel compute the Logsums for this specific demographic group
        zoneList.parallelStream().forEach(origin -> {
            int oIdx = zoneList.indexOf(origin); 
            for (int dIdx = 0; dIdx < numZones; dIdx++) {
                Zone destination = zoneList.get(dIdx);
                var travelMetrics = matrices.getTravelAndAccessibilityMetrics(origin, destination);
                matrix[oIdx][dIdx] = (float) Calculations.calculateLogsum(modeFormulas, travelMetrics, expressionEvaluator);
            }
        });

        return matrix;
    }

    public void clearCache() {
        cache.clear();
    }
}