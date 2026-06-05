package ch.sbb.matsim.plans.models.steps;

import ch.sbb.matsim.plans.matrices.Matrices;
import ch.sbb.matsim.plans.models.formulas.ModeImpedanceFormulaRepository;
import ch.sbb.matsim.plans.models.utils.Calculations;
import ch.sbb.matsim.plans.models.utils.ExpressionEvaluator;
import ch.sbb.matsim.plans.models.steps.demandstrata.DemandStrata;
import ch.sbb.matsim.zones.Zone;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

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
     * Pre-computes all matrices sequentially.
     * This avoids ForkJoinPool starvation caused by nested parallel streams.
     */
    public void precomputeAll(Set<String> activities, Set<DemandStrata> allStrata) {
        log.info("Pre-computing all Secondary Logsum matrices to prevent thread contention...");
        for (String activityType : activities) {
            for (DemandStrata strata : allStrata) {
                getOrComputeLogsumMatrix(activityType, strata);
            }
        }
        log.info("Finished pre-computing {} logsum matrices.", cache.size());
    }

    /**
     * Lazily gets or computes the logsum matrix for a specific activity and stratum.
     */
    public float[][] getOrComputeLogsumMatrix(String activityType, DemandStrata strata) {
        String key = activityType + "_" + strata.getName();

        return cache.computeIfAbsent(key, k -> {
            log.info("Computing Secondary Logsum matrix for {}...", key);
            float[][] matrix = computeMatrix(activityType, strata);
            log.info("Completed computing Secondary Logsum matrix for {}.", key);
            return matrix;
        });
    }

    private float[][] computeMatrix(String activityType, DemandStrata strata) {
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

        IntStream.range(0, numZones).parallel().forEach(oIdx -> {
            Zone origin = zoneList.get(oIdx);
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