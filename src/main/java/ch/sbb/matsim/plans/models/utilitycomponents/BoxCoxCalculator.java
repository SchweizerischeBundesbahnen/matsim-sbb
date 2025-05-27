package ch.sbb.matsim.plans.models.utilitycomponents;

import ch.sbb.matsim.plans.models.resolver.AttributeResolver;
import ch.sbb.matsim.plans.models.resolver.NestedAttributeResolver;
import org.matsim.utils.objectattributes.attributable.Attributable;

public class BoxCoxCalculator implements UtilityComponent {

    private final String attributePath;
    private final double beta;
    private final double lambda;
    private final AttributeResolver resolver;


    public BoxCoxCalculator(String attributePath, double beta, double lambda, AttributeResolver resolver) {
        this.attributePath = attributePath;
        this.beta = beta;
        this.lambda = lambda;
        this.resolver = resolver;
    }

    public BoxCoxCalculator(String attributePath, double beta, double lambda) {
        this(attributePath, beta, lambda, new NestedAttributeResolver());
    }


    @Override
    public double calculate(Attributable attributable) {
        Object attribute = resolver.resolve(attributable, attributePath);
        double value = ((Number) attribute).doubleValue();
        if (lambda == 0) {
            return Math.log(value) * beta;
        } else {
            return ((Math.pow(value, lambda) - 1) / lambda) * beta;
        }
    }


}
