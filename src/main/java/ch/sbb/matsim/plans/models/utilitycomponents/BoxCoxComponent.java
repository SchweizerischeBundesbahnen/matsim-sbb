package ch.sbb.matsim.plans.models.utilitycomponents;

import ch.sbb.matsim.plans.models.resolver.AttributeResolver;
import ch.sbb.matsim.plans.models.resolver.NestedAttributeResolver;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.utils.objectattributes.attributable.Attributable;

public class BoxCoxComponent implements UtilityComponent {

    private static final Logger log = LogManager.getLogger(BoxCoxComponent.class);

    private final String attributePath;
    private final double beta;
    private final double lambda;
    private final AttributeResolver resolver;
    private final boolean nullable;

    public BoxCoxComponent(String attributePath, double beta, double lambda, AttributeResolver resolver, boolean nullable) {
        this.attributePath = attributePath;
        this.beta = beta;
        this.lambda = lambda;
        this.resolver = resolver;
        this.nullable = nullable;
    }

    public BoxCoxComponent(String attributePath, double beta, double lambda, boolean nullable) {
        this(attributePath, beta, lambda, new NestedAttributeResolver(), nullable);
    }

    @Override
    public double calculate(Attributable attributable) {
        Object attribute = resolver.resolve(attributable, attributePath);
        if (attribute == null) {
            if (nullable) {
                return 0.0;
            } else {
                throw new IllegalArgumentException("Attribute at path '" + attributePath + "' is null but component is not nullable.");
            }
        }
        double value = ((Number) attribute).doubleValue();
        double result;
        if (lambda == 0.0) {
            result = Math.log(value) * beta;
        } else {
            result = ((Math.pow(value, lambda) - 1) / lambda) * beta;
        }


        return result;
    }

}
