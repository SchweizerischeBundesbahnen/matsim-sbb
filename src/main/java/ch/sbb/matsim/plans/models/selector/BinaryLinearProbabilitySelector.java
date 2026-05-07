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

public class BinaryLinearProbabilitySelector implements ChoiceSelector {

    private final Map<String, UtilityCalculator> calculators;
    private final String modelName;
    private final static Logger log = LogManager.getLogger(BinaryLinearProbabilitySelector.class);

    public BinaryLinearProbabilitySelector(Map<String, UtilityCalculator> calculators, String modelName) {
        this.calculators = calculators;
        this.modelName = modelName;
    }

    @Override
    public String select(Attributable attributes) {
        var id = ((Identifiable) attributes).getId();
        Random random = SynPopUtils.getRandom(id, modelName);
        Map<String, Double> probabilities = getProbabilities(attributes);
        return drawByProbability(probabilities, random);
    }

    @Override
    public Map<String, Double> getProbabilities(Attributable attributes) {
        Map<String, Double> probabilities = new LinkedHashMap<>();

        // In this linear probability model, the CSV defines the utility for alternative "1"
        UtilityCalculator calc1 = calculators.get("1");
        if (calc1 == null) {
            throw new IllegalStateException("BinaryLinearProbabilitySelector expects a calculator for alternative '1'. Model: " + modelName);
        }

        double u = calc1.calculate(attributes);

        if (Double.isNaN(u)) {
            log.error("Utility for '1' is NaN. Check the utility components. Model: " + modelName);
            u = 0.0;
        }

        // Python Equivalent: utils[utils < 0] = 0; utils[utils > 1] = 1;
        double prob1 = Math.max(0.0, Math.min(1.0, u));
        double prob0 = 1.0 - prob1;

        // Python Equivalent: utils = np.array([1 - utils, utils])
        probabilities.put("1", prob1);
        probabilities.put("0", prob0);

        return probabilities;
    }

    private String drawByProbability(Map<String, Double> probabilities, Random random) {
        double rand = random.nextDouble();
        double cumulative = 0.0;

        for (Map.Entry<String, Double> entry : probabilities.entrySet()) {
            cumulative += entry.getValue();
            if (rand <= cumulative) {
                return entry.getKey();
            }
        }

        // Fallback for extreme rounding errors
        return "0";
    }
}