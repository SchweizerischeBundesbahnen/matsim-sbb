package ch.sbb.matsim.plans.models.utilitycomponents;

import ch.sbb.matsim.plans.models.resolver.AttributeResolver;
import ch.sbb.matsim.plans.models.resolver.NestedAttributeResolver;
import org.matsim.utils.objectattributes.attributable.Attributable;

public class LogarithmicComponent implements UtilityComponent {

    private final String attributePath;
    private final double beta;
    private final AttributeResolver resolver;


    public LogarithmicComponent(String attributePath, double beta, AttributeResolver resolver) {
        this.attributePath = attributePath;
        this.beta = beta;
        this.resolver = resolver;
    }

    public LogarithmicComponent(String attributePath, double beta) {
        this(attributePath, beta, new NestedAttributeResolver());
    }

    @Override
    public double calculate(Attributable attributable) {
        Object attribute = resolver.resolve(attributable, attributePath);
        double value = ((Number) attribute).doubleValue();
        if (value < 0) {
            throw new IllegalArgumentException("Value must be greater than zero for logarithmic calculation.");
        }
        if (value == 0) {
            return 0.0; // Logarithm of zero is undefined, return zero or handle as needed
        }
        return value * Math.log(value) * beta;
    }
}
