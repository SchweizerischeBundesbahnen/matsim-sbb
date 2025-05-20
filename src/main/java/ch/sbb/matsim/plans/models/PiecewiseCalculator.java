package ch.sbb.matsim.plans.models;

import org.matsim.utils.objectattributes.attributable.Attributable;

public class PiecewiseCalculator implements UtilityComponent {

    private final double[] thresholds;
    private final double[] betas;
    private final String attributeName;

    public PiecewiseCalculator(String attributeName, double[] thresholds, double[] betas) {
        this.attributeName = attributeName;
        this.thresholds = thresholds;
        this.betas = betas;
        if (thresholds.length != betas.length - 1) {
            throw new IllegalArgumentException("Thresholds and betas must have length n and n+1 respectively");
        }
    }


    public double calculate(Attributable attributable) {
        double value = ((Number) attributable.getAttributes().getAttribute(attributeName)).doubleValue();
        double returnValue = 0.0;
        for (int i = 0; i < betas.length; i++) {
            if (i == 0) {
                returnValue += betas[i] * Math.min(value, thresholds[i]);
            } else if (i < betas.length - 1) {
                returnValue += betas[i] * (Math.max(0, Math.min(value - thresholds[i - 1],
                        thresholds[i] - thresholds[i - 1])));
            } else {
                returnValue += betas[i] * Math.max(0, value - thresholds[i - 1]);
            }
        }
        return returnValue;
    }

}
