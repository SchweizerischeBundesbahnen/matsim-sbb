package ch.sbb.matsim.plans.models.selector;

import ch.sbb.matsim.plans.models.utilitycomponents.UtilityCalculator;
import org.matsim.utils.objectattributes.attributable.Attributable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

public class LogitSelector implements ChoiceSelector {

    private final Map<String, UtilityCalculator> calculators;
    private final double scaleParameter;
    private final Random random;

    public LogitSelector(Map<String, UtilityCalculator> calculators, double scaleParameter, Random random) {
        this.calculators = calculators;
        this.scaleParameter = scaleParameter;
        this.random = random;
    }

    @Override
    public String select(Attributable attributes) {
        Map<String, Double> probabilities = getProbabilities(attributes);
        return drawByProbability(probabilities);
    }

    @Override
    public Map<String, Double> getProbabilities(Attributable attributes) {
        Map<String, Double> expUtilities = new LinkedHashMap<>();
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

    private String drawByProbability(Map<String, Double> probabilities) {
        double rand = random.nextDouble();
        double cumulative = 0.0;

        for (Map.Entry<String, Double> entry : probabilities.entrySet()) {
            cumulative += entry.getValue();
            if (rand <= cumulative) {
                return entry.getKey();
            }
        }

        throw new IllegalStateException("Probabilistic draw failed – check if probabilities sum to 1.");
    }
}
