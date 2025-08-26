package ch.sbb.matsim.plans.models.utils;

import java.util.Map;
import java.util.Random;

public final class ChoiceUtils {

    /**
     * Private constructor to prevent instantiation.
     */
    private ChoiceUtils() {
    }

    /**
     * Selects an item from a map of items to probabilities using a weighted random choice.
     *
     * @param probabilities A map where keys are the items to choose from (e.g., Id<Zone>) and values are their probabilities.
     *                      The sum of probabilities does not need to equal 1.0; it will be normalized.
     * @param <T>           The type of item to choose.
     * @return The chosen item, or null if the map is empty.
     */
    public static <T> T selectFromProbabilities(Map<T, Double> probabilities, Random random) {
        if (probabilities == null || probabilities.isEmpty()) {
            return null;
        }

        // Normalize probabilities in case they don't sum to 1.0
        double totalProb = probabilities.values().stream().mapToDouble(Double::doubleValue).sum();
        if (totalProb <= 0) {
            // If no positive probabilities, cannot make a choice
            return null;
        }

        double randomValue = random.nextDouble() * totalProb;
        double cumulativeProb = 0.0;

        for (Map.Entry<T, Double> entry : probabilities.entrySet()) {
            cumulativeProb += entry.getValue();
            if (randomValue <= cumulativeProb) {
                return entry.getKey();
            }
        }

        // Fallback in case of floating point precision issues, return the last item.
        return probabilities.keySet().stream().reduce((first, second) -> second).orElse(null);
    }
}