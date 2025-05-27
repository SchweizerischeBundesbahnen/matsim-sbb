package ch.sbb.matsim.plans.models.utilitycomponents;

import ch.sbb.matsim.plans.models.resolver.AttributeResolver;
import ch.sbb.matsim.plans.models.resolver.NestedAttributeResolver;
import org.matsim.utils.objectattributes.attributable.Attributable;

public class LinearCalculator implements UtilityComponent {

    private final String attributePath;
    private final double beta;
    private final AttributeResolver resolver;


    public LinearCalculator(String attributePath, double beta, AttributeResolver resolver) {
        this.attributePath = attributePath;
        this.beta = beta;
        this.resolver = resolver;
    }

    public LinearCalculator(String attributePath, double beta) {
        this(attributePath, beta, new NestedAttributeResolver());
    }

    @Override
    public double calculate(Attributable attributable) {
        Object attribute = resolver.resolve(attributable, attributePath);
        double value = ((Number) attribute).doubleValue();
        return value * beta;
    }
}
