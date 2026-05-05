package ch.sbb.matsim.plans.models.steps.csvmodels;

import ch.sbb.matsim.plans.models.steps.ChoiceModel;
import ch.sbb.matsim.plans.synpop.SynPopUtils;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.utils.objectattributes.attributable.Attributable;

import java.util.List;
import java.util.Random;

public class ASCFittingToTargets {

    /**
     * Fits a logit model to a hard target using Newton-Raphson Shadow Pricing,
     * then executes the probabilistic draws.
     *
     * @param entities A list of MATSim objects (Persons, Households) that have Attributes and an Id.
     * @param targetCount The absolute number of true outcomes desired.
     * @param model The Boolean selection model returning raw probabilities.
     * @param attributeName The attribute key where the boolean result will be saved.
     */
    public static <T extends Attributable & Identifiable<?>> void fitAndAssignTarget(
            List<T> entities, int targetCount, ChoiceModel model, String attributeName, String alternativeName) {

        if (targetCount <= 0) {
            entities.forEach(e -> e.getAttributes().putAttribute(attributeName, false));
            return;
        }
        if (targetCount >= entities.size()) {
            entities.forEach(e -> e.getAttributes().putAttribute(attributeName, true));
            return;
        }

        // 1. Get base utilities for everyone (Reverse-engineered from probabilities)
        double[] baseUtils = new double[entities.size()];
        for (int i = 0; i < entities.size(); i++) {
            double p = model.getProbabilityForAlternative(entities.get(i),alternativeName);

            // Clamp to avoid Infinity errors
            p = Math.max(1e-9, Math.min(1.0 - 1e-9, p));
            baseUtils[i] = Math.log(p / (1.0 - p));
        }

        // 2. Newton-Raphson Solver to find the exact Shadow Price (alpha)
        double alpha = 0.0;
        for (int iter = 0; iter < 50; iter++) { // Increased to 50 iterations
            double expected = 0.0;
            double derivative = 0.0;

            for (int i = 0; i < entities.size(); i++) {
                double u = baseUtils[i] + alpha;
                double p = 1.0 / (1.0 + Math.exp(-u));
                expected += p;
                derivative += p * (1.0 - p); // Derivative of Logit probability
            }

            if (Math.abs(expected - targetCount) < 1.0) break; // Converged perfectly!

            // Calculate the raw Newton step
            double step = (targetCount - expected) / Math.max(derivative, 1e-6);

            step = Math.max(-2.0, Math.min(2.0, step));

            alpha += step;
        }

        // 3. Final assignment using the adjusted probabilities
        for (int i = 0; i < entities.size(); i++) {
            T entity = entities.get(i);

            double u = baseUtils[i] + alpha;
            double finalProb = 1.0 / (1.0 + Math.exp(-u));

            Random random = SynPopUtils.getRandom(entity.getId(), ASCFittingToTargets.class.getName() + "_" + attributeName);
            boolean choice = random.nextDouble() <= finalProb;

            entity.getAttributes().putAttribute(attributeName, choice);
        }
    }
}