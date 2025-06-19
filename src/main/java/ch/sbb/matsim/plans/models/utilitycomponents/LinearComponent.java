package ch.sbb.matsim.plans.models.utilitycomponents;

import ch.sbb.matsim.plans.models.resolver.AttributeResolver;
import ch.sbb.matsim.plans.models.resolver.NestedAttributeResolver;
import org.matsim.api.core.v01.Identifiable;
import org.matsim.utils.objectattributes.attributable.Attributable;

public class LinearComponent implements UtilityComponent {

    private final String attributePath;
    private final double beta;
    private final AttributeResolver resolver;


    public LinearComponent(String attributePath, double beta, AttributeResolver resolver) {
        this.attributePath = attributePath;
        this.beta = beta;
        this.resolver = resolver;
    }

    public LinearComponent(String attributePath, double beta) {
        this(attributePath, beta, new NestedAttributeResolver());
    }

    @Override
    public double calculate(Attributable attributable) {
        Object attribute = resolver.resolve(attributable, attributePath);
        if (attribute == null) {
            throw new RuntimeException("Attribute '" + attributePath + "' is null for " + ((Identifiable) attributable).getId() + ". ");

        }
        double value = attribute instanceof Boolean ? 1.0 : ((Number) attribute).doubleValue();
        return value * beta;
    }
}
