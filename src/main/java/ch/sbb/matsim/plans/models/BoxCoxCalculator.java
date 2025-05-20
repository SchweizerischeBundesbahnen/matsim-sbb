package ch.sbb.matsim.plans.models;

import org.matsim.utils.objectattributes.attributable.Attributable;

public class BoxCoxCalculator implements UtilityComponent {

    private final String attributeName;
    private final double beta;
    private final double lambda;

    public BoxCoxCalculator(String attributeName, double beta, double lambda) {
        this.attributeName = attributeName;
        this.beta = beta;
        this.lambda = lambda;
    }


    @Override
    public double calculate(Attributable attributable) {
        double value = ((Number) attributable.getAttributes().getAttribute(attributeName)).doubleValue();
        if (lambda == 0) {
            return Math.log(value) * beta;
        } else {
            return ((Math.pow(value, lambda) - 1) / lambda) * beta;
        }
    }


}
