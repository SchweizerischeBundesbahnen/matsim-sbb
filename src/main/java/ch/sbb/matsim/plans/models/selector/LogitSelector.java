package ch.sbb.matsim.plans.models.selector;

import ch.sbb.matsim.plans.models.utilitycomponents.UtilityCalculator;
import ch.sbb.matsim.plans.synpop.SynPopUtils;
import org.apache.logging.log4j.LogManager;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.utils.objectattributes.attributable.Attributable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class LogitSelector implements ChoiceSelector {

    private final Map<String, UtilityCalculator> calculators;
    private final double scaleParameter;
    private final String modelName;

    public LogitSelector(Map<String, UtilityCalculator> calculators, double scaleParameter, String modelName) {
        this.calculators = calculators;
        this.scaleParameter = scaleParameter;
        this.modelName = modelName;
    }

    @Override
    public String select(Attributable attributes) {
        var id = ((Identifiable) attributes).getId();
        Random random = SynPopUtils.getRandom(id, modelName);
        Map<String, Double> probabilities = getProbabilities(attributes);
        String chosenAlternative = drawByProbability(probabilities, random);
        if (chosenAlternative == null) {
            throw new IllegalStateException("No alternative was chosen for " + id + ". Check the probabilities.");
        }
        return chosenAlternative;
    }

    @Override
    public Map<String, Double> getProbabilities(Attributable attributes) {
        Map<String, Double> expUtilities = new HashMap<>();
        for (Map.Entry<String, UtilityCalculator> entry : calculators.entrySet()) {
            double u = entry.getValue().calculate(attributes);
            expUtilities.put(entry.getKey(), Math.exp(scaleParameter * u));
        }
        return normalize(expUtilities);
    }

    private Map<String, Double> normalize(Map<String, Double> raw) {
        double sum = raw.values().stream().mapToDouble(Double::doubleValue).sum();
        if (sum == 0.0) throw new IllegalStateException("All utilities resulted in zero probability.");
        return raw.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue() / sum,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    private String drawByProbability(Map<String, Double> probabilities, Random random) {
        double rand = random.nextDouble();
        double cumulative = 0.0;
//        LogManager.getLogger(getClass()).debug("Probabilities: " + probabilities);
//        LogManager.getLogger(getClass()).debug("Random " + rand);

        for (Map.Entry<String, Double> entry : probabilities.entrySet()) {
            cumulative += entry.getValue();
            if (rand <= cumulative) {
                return entry.getKey();
            }
        }

        LogManager.getLogger(getClass()).error("Probabilistic draw failed – check if probabilities sum to 1:");
        LogManager.getLogger(getClass()).error(probabilities);
        return null;
    }
}
