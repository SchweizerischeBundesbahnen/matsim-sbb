package ch.sbb.matsim.plans.models.utils;

import java.util.Map;

public final class Calculations {

    private static final double MIN_LOGSUM = -20.0;

    /**
     * Private constructor to prevent instantiation.
     */
    private Calculations() {
    }

    public static double calculateLogsum(Map<String, String> modeFormulas, Map<String, Double> travelMetrics) {
        double sumOfExponentials = 0.0;
        ExpressionEvaluator expressionEvaluator = new ExpressionEvaluator();
        for (String formula : modeFormulas.values()) {
            if (formula.trim().equals("-9999")) continue;
            double modeUtility = 0.0;
            try {
                modeUtility = expressionEvaluator.evaluate(formula, travelMetrics);
            } catch (Exception e) {
                continue;
            }


            // Add a check here to prevent individual utilities from being too negative,
            // which is good practice but the main fix is below.
            if (modeUtility < -50) { // An arbitrary but safe floor
                continue; // exp(-50) is effectively zero, skip adding it.
            }

            sumOfExponentials += Math.exp(modeUtility);
        }

        if (sumOfExponentials == 0.0) {
            return MIN_LOGSUM;
        }

        double logsum = Math.log(sumOfExponentials);

        // Ensure the final logsum does not fall below the defined floor.
        return Math.max(logsum, MIN_LOGSUM);
    }
}

