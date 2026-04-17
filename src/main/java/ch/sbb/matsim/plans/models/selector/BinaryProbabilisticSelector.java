package ch.sbb.matsim.plans.models.selector;

import ch.sbb.matsim.plans.models.utilitycomponents.UtilityCalculator;
import ch.sbb.matsim.plans.synpop.SynPopUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.utils.objectattributes.attributable.Attributable;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

public class BinaryProbabilisticSelector implements ChoiceSelector {

    private final Map<String, UtilityCalculator> calculators;
    private final String modelName;
    private final static Logger log = LogManager.getLogger(BinaryProbabilisticSelector.class);

    public BinaryProbabilisticSelector(Map<String, UtilityCalculator> calculators, String modelName) {
        this.calculators = calculators;
        this.modelName = modelName;
    }

    @Override
    public String select(Attributable attributes) {
        @SuppressWarnings("rawtypes") var id = ((Identifiable) attributes).getId();
        @SuppressWarnings("unchecked") Random random = SynPopUtils.getRandom(id, modelName);
        Map<String, Double> probabilities = getProbabilities(attributes);
        String chosenAlternative = drawByProbability(probabilities, random);
        if (chosenAlternative == null) {
            throw new IllegalStateException("No alternative was chosen for " + id + ". Check the probabilities. Model: " + modelName + ", Probabilities: " + probabilities);
        }
        return chosenAlternative;
    }
    @Override
    public Map<String, Double> getProbabilities(Attributable attributes) {
        Map<String, Double> utilities = new LinkedHashMap<>();
        double maxUtility = Double.NEGATIVE_INFINITY;

        // 1. Calculate all utilities and find the maximum scaled utility
        for (Map.Entry<String, UtilityCalculator> entry : calculators.entrySet()) {
            double u = entry.getValue().calculate(attributes);

            if (Double.isNaN(u)) {
                log.error("Utility for alternative '" + entry.getKey() + "' is NaN. Check the utility components. Model: " + modelName);
                u = Double.NEGATIVE_INFINITY; // Treat NaN as impossible
            }

            utilities.put(entry.getKey(), u);

            if (u > maxUtility) {
                maxUtility = u;
            }
        }

        utilities.put("0", 1-utilities.get("1"));

        // Fast-fail if all alternatives are impossible
        if (maxUtility == Double.NEGATIVE_INFINITY) {
            throw new IllegalStateException("All utilities evaluated to negative infinity for model: " + modelName);
        }

        // 3. Normalize to probabilities
        return utilities;
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