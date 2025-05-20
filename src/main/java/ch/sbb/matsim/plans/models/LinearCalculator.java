package ch.sbb.matsim.plans.models;

import org.matsim.utils.objectattributes.attributable.Attributable;

public class LinearCalculator implements UtilityComponent {

    private final String attributeName;
    private final double beta;

    public LinearCalculator(String attributeName, double beta) {
        this.attributeName = attributeName;
        this.beta = beta;
    }

    @Override
    public double calculate(Attributable attributable) {
        double value = ((Number) attributable.getAttributes().getAttribute(attributeName)).doubleValue();
        return value * beta;
    }
}
